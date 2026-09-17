package d6nggyun.stay.adapter.b;

import d6nggyun.stay.adapter.SupplierAdapter;
import d6nggyun.stay.adapter.TransportErrorClassifier;
import d6nggyun.stay.adapter.b.dto.BPropertiesResponse;
import d6nggyun.stay.adapter.b.dto.BSearchResponse;
import d6nggyun.stay.adapter.result.SupplierCatalog;
import d6nggyun.stay.adapter.result.SupplierOffer;
import d6nggyun.stay.adapter.result.SupplierSearchResult;
import d6nggyun.stay.domain.DailyAvailability;
import d6nggyun.stay.domain.Price;
import d6nggyun.stay.domain.SearchCriteria;
import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.global.exception.SupplierFailureKind;
import d6nggyun.stay.global.exception.SupplierIntegrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Supplier B 어댑터. WebClient로 B API를 호출하고 응답을 표준 모델로 정규화한다.
 *
 * B의 특징을 이 계층에서 흡수한다.
 * - 실패: 장애 시에도 HTTP 200을 주므로, 본문 resultCode("0000"이 성공)를 확인해 실패를 판정한다.
 *   A의 HTTP 4xx/5xx와 동일하게 SupplierIntegrationException으로 변환한다.
 * - 요금: 세금 포함 숙박 전체 총액(totalPrice)을 그대로 세금 포함 총액으로 사용한다.
 */
@RequiredArgsConstructor
public class SupplierBAdapter implements SupplierAdapter {

    private static final String SUCCESS_CODE = "0000";
    /**
     * 일시적(재시도 가능) resultCode. B는 실패를 본문 코드로 표현하므로, 서버측 일시 장애 코드(E503 등)는
     * A의 HTTP 5xx와 동일하게 재시도 대상으로 본다. 그 외 resultCode(잘못된 요청·업무 실패)는 결정적이라 비재시도.
     * 실제 코드 체계는 공급사 스펙을 따른다.
     */
    private static final Set<String> RETRYABLE_RESULT_CODES = Set.of("E503", "E429");

    private final WebClient webClient;

    @Override
    public SupplierType supplier() {
        return SupplierType.SUPPLIER_B;
    }

    @Override
    public Mono<SupplierCatalog> fetchCatalog() {
        return webClient.get()
                .uri("/b/api/properties")
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toIntegrationError) // 방어적: 실제 HTTP 오류도 실패로
                .bodyToMono(BPropertiesResponse.class)
                .flatMap(this::verifyResultCode)  // HTTP 200이어도 resultCode != "0000"이면 실패
                .map(this::toCatalog)
                .onErrorMap(this::wrapUnlessIntegrationError);
    }

    @Override
    public Mono<SupplierSearchResult> search(SearchCriteria criteria, List<String> supplierStayCodes) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/b/api/search")
                        .queryParam("propertyIds", String.join(",", supplierStayCodes))
                        .queryParam("checkIn", criteria.checkIn())
                        .queryParam("checkOut", criteria.checkOut())
                        .queryParam("adults", criteria.adults())
                        .queryParam("children", criteria.children())
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toIntegrationError)
                .bodyToMono(BSearchResponse.class)
                .flatMap(this::verifyResultCode)  // HTTP 200이어도 resultCode != "0000"이면 실패
                .map(response -> toSearchResult(criteria, response))
                .onErrorMap(this::wrapUnlessIntegrationError);
    }

    private Mono<BPropertiesResponse> verifyResultCode(BPropertiesResponse response) {
        if (!SUCCESS_CODE.equals(response.resultCode())) {
            return Mono.error(resultCodeError(response.resultCode()));
        }
        return Mono.just(response);
    }

    private Mono<BSearchResponse> verifyResultCode(BSearchResponse response) {
        if (!SUCCESS_CODE.equals(response.resultCode())) {
            return Mono.error(resultCodeError(response.resultCode()));
        }
        return Mono.just(response);
    }

    private SupplierCatalog toCatalog(BPropertiesResponse response) {
        List<SupplierCatalog.Stay> stays = response.data().items().stream()
                .map(property -> new SupplierCatalog.Stay(
                        property.propertyId(),
                        property.propertyName(),
                        property.rooms().stream()
                                .map(room -> new SupplierCatalog.RoomType(
                                        room.roomId(), room.roomName(), room.maxOccupancy()))
                                .toList()))
                .toList();
        return new SupplierCatalog(SupplierType.SUPPLIER_B, stays);
    }

    private SupplierSearchResult toSearchResult(SearchCriteria criteria, BSearchResponse response) {
        List<SupplierOffer> offers = response.data().items().stream()
                .map(item -> toOffer(criteria, item))
                .toList();
        return new SupplierSearchResult(SupplierType.SUPPLIER_B, offers);
    }

    private SupplierOffer toOffer(SearchCriteria criteria, BSearchResponse.Item item) {
        // B는 이미 세금 포함 총액을 주므로 그대로 사용한다.
        Price price = Price.of(item.currency(), item.totalPrice(), criteria.nights());

        Map<LocalDate, Integer> remainingByDate = item.inventory().stream()
                .collect(Collectors.toMap(
                        BSearchResponse.Inventory::date,
                        BSearchResponse.Inventory::remainingRooms));
        DailyAvailability dailyAvailability = new DailyAvailability(remainingByDate);

        return new SupplierOffer(
                SupplierType.SUPPLIER_B,
                item.propertyId(),
                item.propertyName(),
                item.roomId(),
                item.roomName(),
                item.maxOccupancy(),
                dailyAvailability.availableRoomCount(criteria.stayDates()),
                item.breakfastIncluded(),
                price,
                dailyAvailability);
    }

    private SupplierIntegrationException resultCodeError(String resultCode) {
        // HTTP는 200이지만 본문 규약(resultCode)으로 실패를 알린 경우 → 상태는 규약 오류.
        // 다만 일시적 코드(E503 등)는 재시도 대상으로 구분한다(상태·재시도 판정은 독립).
        boolean retryable = RETRYABLE_RESULT_CODES.contains(resultCode);
        return new SupplierIntegrationException(SupplierType.SUPPLIER_B,
                SupplierFailureKind.PROTOCOL_ERROR, retryable, "Supplier B resultCode " + resultCode);
    }

    private Mono<Throwable> toIntegrationError(ClientResponse response) {
        // 5xx는 재시도 대상, 4xx는 비재시도. 상태로는 둘 다 HTTP_ERROR.
        boolean retryable = response.statusCode().is5xxServerError();
        return Mono.error(new SupplierIntegrationException(SupplierType.SUPPLIER_B,
                SupplierFailureKind.HTTP_ERROR, retryable, "Supplier B HTTP " + response.statusCode().value()));
    }

    private Throwable wrapUnlessIntegrationError(Throwable ex) {
        if (ex instanceof SupplierIntegrationException) {
            return ex;
        }
        // 타임아웃·연결 실패는 일시적이라 재시도 대상, 디코딩·형식 오류는 결정적이라 비재시도.
        if (TransportErrorClassifier.isTimeout(ex)) {
            return new SupplierIntegrationException(SupplierType.SUPPLIER_B,
                    SupplierFailureKind.TIMEOUT, true, "Supplier B 연동 실패: " + ex.getMessage(), ex);
        }
        if (TransportErrorClassifier.isConnectionFailure(ex)) {
            return new SupplierIntegrationException(SupplierType.SUPPLIER_B,
                    SupplierFailureKind.HTTP_ERROR, true, "Supplier B 연결 실패: " + ex.getMessage(), ex);
        }
        return new SupplierIntegrationException(SupplierType.SUPPLIER_B,
                SupplierFailureKind.PROTOCOL_ERROR, false, "Supplier B 연동 실패: " + ex.getMessage(), ex);
    }
}
