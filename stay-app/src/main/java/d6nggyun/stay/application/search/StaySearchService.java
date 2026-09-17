package d6nggyun.stay.application.search;

import d6nggyun.stay.adapter.SupplierAdapter;
import d6nggyun.stay.adapter.result.SupplierOffer;
import d6nggyun.stay.domain.SearchCriteria;
import d6nggyun.stay.domain.StayOffer;
import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.global.config.SupplierProperties;
import d6nggyun.stay.global.exception.SupplierFailureKind;
import d6nggyun.stay.global.exception.SupplierIntegrationException;
import d6nggyun.stay.infrastructure.persistence.entity.RoomTypeMapping;
import d6nggyun.stay.infrastructure.persistence.entity.StayMapping;
import d6nggyun.stay.infrastructure.persistence.repository.RoomTypeMappingRepository;
import d6nggyun.stay.infrastructure.persistence.repository.StayMappingRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 통합 검색 오케스트레이터. 활성 매핑으로 공급사별 호출 대상을 정하고, 공급사 어댑터를 병렬 호출한 뒤,
 * 응답을 표준 모델로 정규화하고 공급사 코드를 내부 식별자로 치환해 결과와 공급사별 상태를 합친다.
 *
 * 리액티브 경계는 이 서비스 안으로 한정한다. 병렬 조합은 Mono·Flux로 처리하되 끝에서 block()으로 받아,
 * 상위(컨트롤러)는 동기 결과를 받는다. 한 공급사가 실패해도 나머지 공급사 결과는 그대로 반환한다(부분 실패 허용).
 *
 * 견고성(#9): 숙소 코드를 chunk-size 단위로 분할해 동시성 상한으로 호출하고, 청크마다 응답 타임아웃을,
 * 공급사마다 전체 예산을 적용한다. 일부 청크만 성공하면 PARTIAL_SUCCESS, 예산·응답 초과는 TIMEOUT으로 통일한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaySearchService {

    private final List<SupplierAdapter> adapters;
    private final StayMappingRepository stayMappingRepository;
    private final RoomTypeMappingRepository roomTypeMappingRepository;
    private final SupplierProperties properties;
    private final Retry supplierSearchRetry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public SearchResult search(SearchCriteria criteria) {
        // 1. 매핑을 일괄 로딩한다. 네트워크 호출 전에 필요한 값을 모두 확보해 조회 트랜잭션을 짧게 유지한다.
        List<StayMapping> stayMappings = stayMappingRepository.findAll();
        List<RoomTypeMapping> roomTypeMappings = roomTypeMappingRepository.findAll();

        // 2. 공급사별 호출 대상 코드 그룹화 + 코드 → 내부 식별자 치환용 조회 맵 구성
        Map<SupplierType, List<String>> codesBySupplier = stayMappings.stream()
                .collect(Collectors.groupingBy(
                        StayMapping::getSupplier,
                        Collectors.mapping(StayMapping::getSupplierStayCode, Collectors.toList())));
        Map<StayKey, Long> stayIdByKey = stayMappings.stream()
                .collect(Collectors.toMap(
                        m -> new StayKey(m.getSupplier(), m.getSupplierStayCode()),
                        StayMapping::getInternalStayId,
                        (existing, ignored) -> existing));
        Map<RoomTypeKey, Long> roomTypeIdByKey = roomTypeMappings.stream()
                .collect(Collectors.toMap(
                        m -> new RoomTypeKey(m.getSupplier(), m.getSupplierStayCode(), m.getSupplierRoomTypeCode()),
                        RoomTypeMapping::getInternalRoomTypeId,
                        (existing, ignored) -> existing));

        // 3. 공급사별 호출 파이프라인을 조립한다(아직 실행 전). 매핑 없는 공급사는 호출하지 않고 SKIPPED로 둔다.
        List<Mono<SupplierResult>> calls = adapters.stream()
                .map(adapter -> {
                    List<String> codes = codesBySupplier.getOrDefault(adapter.supplier(), List.of());
                    if (codes.isEmpty()) {
                        return Mono.just(SupplierResult.skipped(adapter.supplier()));
                    }
                    return callSupplier(adapter, criteria, codes, stayIdByKey, roomTypeIdByKey);
                })
                .toList();

        // 4. 병렬 실행 후 결과를 블로킹으로 수집한다. merge는 완료 순서로 섞이므로 공급사 순서로 정렬해 응답을 안정화한다.
        List<SupplierResult> supplierResults = Flux.merge(calls).collectList().block();
        if (supplierResults == null) {
            supplierResults = List.of();
        }
        List<SupplierResult> ordered = supplierResults.stream()
                .sorted(Comparator.comparing(SupplierResult::supplier))
                .toList();

        return new SearchResult(ordered);
    }

    /**
     * 한 공급사를 청크 단위로 병렬 호출해 하나의 공급사 결과로 합친다.
     * defer로 구독 시점에 지연 측정을 시작하고, 청크마다 응답 타임아웃을, 공급사 전체에 예산을 적용한다.
     */
    private Mono<SupplierResult> callSupplier(SupplierAdapter adapter, SearchCriteria criteria, List<String> codes,
                                             Map<StayKey, Long> stayIdByKey, Map<RoomTypeKey, Long> roomTypeIdByKey) {
        SupplierType supplier = adapter.supplier();
        SupplierProperties.Search cfg = properties.search();
        Duration responseTimeout = Duration.ofMillis(cfg.responseTimeoutMs());
        Duration budget = Duration.ofMillis(cfg.requestBudgetMs());
        // 청크 크기는 설정값(supplier.search.chunk-size)을 그대로 쓴다. 공급사 규약상 상한(최대 50)은
        // 코드에 중복으로 박지 않고 설정값과 그 주석으로 명시한다. 여기서는 0/음수만 분할 알고리즘 보호를 위해 막는다.
        List<List<String>> chunks = partition(codes, Math.max(1, cfg.chunkSize()));
        int concurrency = Math.max(1, cfg.chunkConcurrency());
        // 서킷은 공급사별 독립 인스턴스. 재시도는 공통 정책이라 하나를 공유한다.
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(supplier.name());

        return Mono.defer(() -> {
            long start = System.nanoTime();
            return Flux.fromIterable(chunks)
                    .flatMap(chunk -> callChunk(adapter, criteria, chunk, responseTimeout, circuitBreaker), concurrency)
                    .collectList()
                    .map(outcomes -> aggregate(supplier, outcomes, elapsedMs(start), stayIdByKey, roomTypeIdByKey))
                    // 전체 예산 초과 시(여러 청크가 순차로 쌓이는 확장 상황) 그 공급사를 TIMEOUT으로 마감한다.
                    .timeout(budget, Mono.fromSupplier(() -> SupplierResult.failure(
                            supplier, SupplierSearchStatus.TIMEOUT, elapsedMs(start),
                            SupplierSearchStatus.TIMEOUT.name(),
                            "요청 예산(" + cfg.requestBudgetMs() + "ms) 초과")));
        });
    }

    /**
     * 청크 하나를 호출한다. 재시도·서킷을 얹은 뒤, 응답 타임아웃·연동 실패를 예외로 던지지 않고 청크 결과로 흡수한다.
     * 연산자 순서: (call+응답 타임아웃) → 재시도 → 서킷(가장 바깥). 에러를 결과로 바꾸는 onErrorResume은
     * 재시도·서킷이 실제 예외를 보고 판정하도록 반드시 그 뒤에 둔다.
     */
    private Mono<ChunkOutcome> callChunk(SupplierAdapter adapter, SearchCriteria criteria,
                                        List<String> chunk, Duration responseTimeout, CircuitBreaker circuitBreaker) {
        return adapter.search(criteria, chunk)
                .timeout(responseTimeout)
                .transformDeferred(RetryOperator.of(supplierSearchRetry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .map(result -> ChunkOutcome.success(result.offers()))
                // 타임아웃은 Reactor 내부 문구 대신 사유를 명확히 담고, 나머지는 예외 메시지를 쓴다.
                .onErrorResume(ex -> Mono.just(ChunkOutcome.failure(classify(ex),
                        ex instanceof TimeoutException
                                ? "응답 타임아웃(" + responseTimeout.toMillis() + "ms) 초과"
                                : messageOf(ex))));
    }

    /** 청크 결과들을 공급사 결과로 합친다. 전부 성공/일부 성공/전부 실패를 상태로 구분한다. */
    private SupplierResult aggregate(SupplierType supplier, List<ChunkOutcome> outcomes, long latencyMs,
                                    Map<StayKey, Long> stayIdByKey, Map<RoomTypeKey, Long> roomTypeIdByKey) {
        List<ChunkOutcome> failed = outcomes.stream().filter(o -> !o.success()).toList();
        List<StayOffer> offers = outcomes.stream()
                .filter(ChunkOutcome::success)
                .flatMap(o -> toStayOffers(o.offers(), stayIdByKey, roomTypeIdByKey).stream())
                .toList();

        if (failed.isEmpty()) {
            return SupplierResult.success(supplier, latencyMs, offers);
        }
        ChunkOutcome firstFailure = failed.get(0);
        if (failed.size() < outcomes.size()) {
            // 성공 청크가 하나라도 있으면 결과를 버리지 않고 부분 성공으로 표기한다.
            return SupplierResult.partialSuccess(
                    supplier, latencyMs, offers, firstFailure.errorCode(), firstFailure.errorMessage());
        }
        // 전부 실패: 대표 상태를 우선순위(TIMEOUT > HTTP_ERROR > PROTOCOL_ERROR)로 정한다.
        SupplierSearchStatus status = representativeStatus(failed);
        log.warn("공급사 검색 실패. supplier={}, status={}, message={}", supplier, status, firstFailure.errorMessage());
        return SupplierResult.failure(supplier, status, latencyMs, status.name(), firstFailure.errorMessage());
    }

    /** 정규화된 공급사 offer의 공급사 코드를 내부 식별자로 치환해 표준 StayOffer를 만든다. */
    private List<StayOffer> toStayOffers(List<SupplierOffer> supplierOffers,
                                         Map<StayKey, Long> stayIdByKey, Map<RoomTypeKey, Long> roomTypeIdByKey) {
        List<StayOffer> offers = new ArrayList<>();
        for (SupplierOffer offer : supplierOffers) {
            Long internalStayId = stayIdByKey.get(new StayKey(offer.supplier(), offer.supplierStayCode()));
            Long internalRoomTypeId = roomTypeIdByKey.get(
                    new RoomTypeKey(offer.supplier(), offer.supplierStayCode(), offer.supplierRoomTypeCode()));
            if (internalStayId == null || internalRoomTypeId == null) {
                // 로딩 이후 매핑이 사라지는 등으로 치환할 내부 식별자가 없으면, 코드 노출 대신 방어적으로 제외한다.
                log.warn("내부 식별자 매핑이 없어 offer를 제외합니다. supplier={}, stayCode={}, roomTypeCode={}",
                        offer.supplier(), offer.supplierStayCode(), offer.supplierRoomTypeCode());
                continue;
            }
            offers.add(new StayOffer(
                    internalStayId,
                    offer.stayName(),
                    internalRoomTypeId,
                    offer.roomTypeName(),
                    offer.maxOccupancy(),
                    offer.availableRoomCount(),
                    offer.supplier(),
                    offer.breakfastIncluded(),
                    offer.price(),
                    offer.dailyAvailability()));
        }
        return offers;
    }

    /** 예외를 공급사별 상태로 통일한다. 서킷 차단은 CIRCUIT_OPEN, 타임아웃은 TIMEOUT, 연동 실패는 실패 종류대로 매핑한다. */
    private SupplierSearchStatus classify(Throwable ex) {
        if (ex instanceof CallNotPermittedException) {
            return SupplierSearchStatus.CIRCUIT_OPEN;
        }
        if (ex instanceof TimeoutException) {
            return SupplierSearchStatus.TIMEOUT;
        }
        if (ex instanceof SupplierIntegrationException integration) {
            return kindToStatus(integration.getKind());
        }
        return SupplierSearchStatus.PROTOCOL_ERROR;
    }

    private SupplierSearchStatus kindToStatus(SupplierFailureKind kind) {
        return switch (kind) {
            case HTTP_ERROR -> SupplierSearchStatus.HTTP_ERROR;
            case PROTOCOL_ERROR -> SupplierSearchStatus.PROTOCOL_ERROR;
            case TIMEOUT -> SupplierSearchStatus.TIMEOUT;
        };
    }

    /** 전부 실패 시 대표 상태. 우선순위: CIRCUIT_OPEN > TIMEOUT > HTTP_ERROR > PROTOCOL_ERROR. */
    private SupplierSearchStatus representativeStatus(List<ChunkOutcome> failed) {
        if (failed.stream().anyMatch(o -> o.failStatus() == SupplierSearchStatus.CIRCUIT_OPEN)) {
            return SupplierSearchStatus.CIRCUIT_OPEN;
        }
        if (failed.stream().anyMatch(o -> o.failStatus() == SupplierSearchStatus.TIMEOUT)) {
            return SupplierSearchStatus.TIMEOUT;
        }
        if (failed.stream().anyMatch(o -> o.failStatus() == SupplierSearchStatus.HTTP_ERROR)) {
            return SupplierSearchStatus.HTTP_ERROR;
        }
        return SupplierSearchStatus.PROTOCOL_ERROR;
    }

    private String messageOf(Throwable ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }

    /** 청크 하나의 처리 결과. 성공 시 offer를, 실패 시 상태·사유를 담는다. */
    private record ChunkOutcome(boolean success, List<SupplierOffer> offers,
                               SupplierSearchStatus failStatus, String errorCode, String errorMessage) {

        static ChunkOutcome success(List<SupplierOffer> offers) {
            return new ChunkOutcome(true, offers, null, null, null);
        }

        static ChunkOutcome failure(SupplierSearchStatus status, String message) {
            return new ChunkOutcome(false, List.of(), status, status.name(), message);
        }
    }

    private record StayKey(SupplierType supplier, String stayCode) {
    }

    private record RoomTypeKey(SupplierType supplier, String stayCode, String roomTypeCode) {
    }
}
