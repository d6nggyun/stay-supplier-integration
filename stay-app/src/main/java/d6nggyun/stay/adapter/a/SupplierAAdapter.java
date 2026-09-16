package d6nggyun.stay.adapter.a;

import d6nggyun.stay.adapter.SupplierAdapter;
import d6nggyun.stay.adapter.a.dto.AAvailabilityResponse;
import d6nggyun.stay.adapter.a.dto.AHotelsResponse;
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
import java.util.stream.Collectors;

/**
 * Supplier A 어댑터. WebClient로 A API를 호출하고 응답을 표준 모델로 정규화한다.
 *
 * A의 특징을 이 계층에서 흡수한다.
 * - 요금: 날짜별 세전 단가(nightlyRate) + 세액(taxAmount)을 날짜별로 합산해 세금 포함 총액을 만든다.
 * - 실패: HTTP 4xx/5xx를 SupplierIntegrationException으로 변환한다.
 */
@RequiredArgsConstructor
public class SupplierAAdapter implements SupplierAdapter {

    private final WebClient webClient;

    @Override
    public SupplierType supplier() {
        return SupplierType.SUPPLIER_A;
    }

    @Override
    public Mono<SupplierCatalog> fetchCatalog() {
        // 아래 체인은 파이프라인을 조립할 뿐이며, 구독(예: 상위 경계의 block) 전까지 실제 호출은 일어나지 않는다.
        return webClient.get()                    // GET 요청 시작
                .uri("/a/v1/hotels")              // baseUrl에 붙일 경로
                .retrieve()                       // 요청 전송 + 본문 추출 준비 (기본적으로 4xx/5xx는 에러로 취급)
                .onStatus(HttpStatusCode::isError, this::toIntegrationError) // 4xx/5xx → 연동 실패 예외로 변환
                .bodyToMono(AHotelsResponse.class) // 응답 본문(JSON)을 DTO로 역직렬화
                .map(this::toCatalog)             // DTO → 표준 카탈로그로 변환
                .onErrorMap(this::wrapUnlessIntegrationError); // 디코딩·연결 등 나머지 에러도 연동 실패로 통일
    }

    @Override
    public Mono<SupplierSearchResult> search(SearchCriteria criteria, List<String> supplierStayCodes) {
        return webClient.get()                    // GET 요청 시작
                .uri(uriBuilder -> uriBuilder     // 경로 + 쿼리 파라미터 조립 (baseUrl에 붙고, 값은 자동 인코딩)
                        .path("/a/v1/availability")
                        .queryParam("hotelCodes", String.join(",", supplierStayCodes))
                        .queryParam("checkIn", criteria.checkIn())
                        .queryParam("checkOut", criteria.checkOut())
                        .queryParam("adults", criteria.adults())
                        .queryParam("children", criteria.children())
                        .build())
                .retrieve()                       // 요청 전송 + 본문 추출 준비
                .onStatus(HttpStatusCode::isError, this::toIntegrationError) // 4xx/5xx → 연동 실패 예외로 변환
                .bodyToMono(AAvailabilityResponse.class)                    // 응답 본문(JSON)을 DTO로 역직렬화
                .map(availability -> toSearchResult(criteria, availability)) // DTO → 표준 offer로 변환(요금 합산·재고 min)
                .onErrorMap(this::wrapUnlessIntegrationError); // 디코딩·연결 등 나머지 에러도 연동 실패로 통일
    }

    private SupplierCatalog toCatalog(AHotelsResponse hotels) {
        List<SupplierCatalog.Stay> stays = hotels.items().stream()
                .map(hotel -> new SupplierCatalog.Stay(
                        hotel.hotelCode(),
                        hotel.hotelName(),
                        hotel.roomTypes().stream()
                                .map(rt -> new SupplierCatalog.RoomType(
                                        rt.roomTypeCode(), rt.roomTypeName(), rt.maxOccupancy()))
                                .toList()))
                .toList();
        return new SupplierCatalog(SupplierType.SUPPLIER_A, stays);
    }

    private SupplierSearchResult toSearchResult(SearchCriteria criteria, AAvailabilityResponse availability) {
        List<SupplierOffer> offers = availability.items().stream()
                .map(item -> toOffer(criteria, item))
                .toList();
        return new SupplierSearchResult(SupplierType.SUPPLIER_A, offers);
    }

    private SupplierOffer toOffer(SearchCriteria criteria, AAvailabilityResponse.Item item) {
        // 세금 포함 총액 = Σ(날짜별 nightlyRate + taxAmount)
        long grossTotalAmount = item.dailyRates().stream()
                .mapToLong(rate -> rate.nightlyRate() + rate.taxAmount())
                .sum();
        Price price = Price.of(item.currency(), grossTotalAmount, criteria.nights());

        Map<LocalDate, Integer> remainingByDate = item.dailyRates().stream()
                .collect(Collectors.toMap(
                        AAvailabilityResponse.DailyRate::date,
                        AAvailabilityResponse.DailyRate::remainingRooms));
        DailyAvailability dailyAvailability = new DailyAvailability(remainingByDate);

        return new SupplierOffer(
                SupplierType.SUPPLIER_A,
                item.hotelCode(),
                item.hotelName(),
                item.roomTypeCode(),
                item.roomTypeName(),
                item.maxOccupancy(),
                dailyAvailability.availableRoomCount(criteria.stayDates()),
                item.breakfastIncluded(),
                price,
                dailyAvailability);
    }

    private Mono<Throwable> toIntegrationError(ClientResponse response) {
        return Mono.error(new SupplierIntegrationException(SupplierType.SUPPLIER_A,
                SupplierFailureKind.HTTP_ERROR, "Supplier A HTTP " + response.statusCode().value()));
    }

    private Throwable wrapUnlessIntegrationError(Throwable ex) {
        if (ex instanceof SupplierIntegrationException) {
            return ex;
        }
        // 디코딩·형식 오류 등 HTTP 계층 밖의 실패는 규약 오류로 통일한다.
        return new SupplierIntegrationException(SupplierType.SUPPLIER_A,
                SupplierFailureKind.PROTOCOL_ERROR, "Supplier A 연동 실패: " + ex.getMessage(), ex);
    }
}
