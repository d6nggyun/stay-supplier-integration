package d6nggyun.stay.application.search;

import d6nggyun.stay.adapter.SupplierAdapter;
import d6nggyun.stay.adapter.result.SupplierOffer;
import d6nggyun.stay.adapter.result.SupplierSearchResult;
import d6nggyun.stay.domain.SearchCriteria;
import d6nggyun.stay.domain.StayOffer;
import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.global.exception.SupplierIntegrationException;
import d6nggyun.stay.infrastructure.persistence.entity.RoomTypeMapping;
import d6nggyun.stay.infrastructure.persistence.entity.StayMapping;
import d6nggyun.stay.infrastructure.persistence.repository.RoomTypeMappingRepository;
import d6nggyun.stay.infrastructure.persistence.repository.StayMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 통합 검색 오케스트레이터. 활성 매핑으로 공급사별 호출 대상을 정하고, 공급사 어댑터를 병렬 호출한 뒤,
 * 응답을 표준 모델로 정규화하고 공급사 코드를 내부 식별자로 치환해 결과와 공급사별 상태를 합친다.
 *
 * 리액티브 경계는 이 서비스 안으로 한정한다. 병렬 조합은 Mono·Flux로 처리하되 끝에서 block()으로 받아,
 * 상위(컨트롤러)는 동기 결과를 받는다. 한 공급사가 실패해도 나머지 공급사 결과는 그대로 반환한다(부분 실패 허용).
 *
 * 개별 호출 타임아웃·전체 예산·50개 초과 청크 분할·PARTIAL_SUCCESS는 #9에서 더한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaySearchService {

    private final List<SupplierAdapter> adapters;
    private final StayMappingRepository stayMappingRepository;
    private final RoomTypeMappingRepository roomTypeMappingRepository;

    public SearchResult search(SearchCriteria criteria) {
        // 1. 활성 매핑을 일괄 로딩한다. 네트워크 호출 전에 필요한 값을 모두 확보해 조회 트랜잭션을 짧게 유지한다.
        List<StayMapping> stayMappings = stayMappingRepository.findByActiveTrue();
        List<RoomTypeMapping> roomTypeMappings = roomTypeMappingRepository.findByActiveTrue();

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

    /** 한 공급사를 호출해 성공 시 표준 offer로, 실패 시 상태로 변환한다. defer로 구독 시점에 지연을 측정한다. */
    private Mono<SupplierResult> callSupplier(SupplierAdapter adapter, SearchCriteria criteria, List<String> codes,
                                             Map<StayKey, Long> stayIdByKey, Map<RoomTypeKey, Long> roomTypeIdByKey) {
        SupplierType supplier = adapter.supplier();
        return Mono.defer(() -> {
            long start = System.nanoTime();
            return adapter.search(criteria, codes)
                    .map(result -> SupplierResult.success(
                            supplier, elapsedMs(start), toStayOffers(result, stayIdByKey, roomTypeIdByKey)))
                    .onErrorResume(SupplierIntegrationException.class,
                            ex -> Mono.just(toFailure(supplier, ex, elapsedMs(start))));
        });
    }

    /** 정규화된 공급사 offer의 공급사 코드를 내부 식별자로 치환해 표준 StayOffer를 만든다. */
    private List<StayOffer> toStayOffers(SupplierSearchResult result,
                                         Map<StayKey, Long> stayIdByKey, Map<RoomTypeKey, Long> roomTypeIdByKey) {
        List<StayOffer> offers = new ArrayList<>();
        for (SupplierOffer offer : result.offers()) {
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

    /** 연동 실패를 공급사별 상태로 변환한다. 실패 종류에 따라 HTTP_ERROR·PROTOCOL_ERROR로 통일한다. */
    private SupplierResult toFailure(SupplierType supplier, SupplierIntegrationException ex, long latencyMs) {
        SupplierSearchStatus status = switch (ex.getKind()) {
            case HTTP_ERROR -> SupplierSearchStatus.HTTP_ERROR;
            case PROTOCOL_ERROR -> SupplierSearchStatus.PROTOCOL_ERROR;
        };
        log.warn("공급사 검색 실패. supplier={}, status={}, message={}", supplier, status, ex.getMessage());
        return SupplierResult.failure(supplier, status, latencyMs, ex.getKind().name(), ex.getMessage());
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private record StayKey(SupplierType supplier, String stayCode) {
    }

    private record RoomTypeKey(SupplierType supplier, String stayCode, String roomTypeCode) {
    }
}
