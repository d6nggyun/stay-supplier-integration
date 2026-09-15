package d6nggyun.stay.adapter.a.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /a/v1/availability — Supplier A 재고·요금(②) 응답. 어댑터 계층 내부 전용 DTO.
 * 날짜별 1박 단가(nightlyRate) + 세액(taxAmount)이 분리되어 있다.
 */
// ignoreUnknown = true: 모르는 필드는 무시한다. 공급사가 응답에 필드를 추가해도 역직렬화가 깨지지 않게 하려는 것.
// (Spring 기본 Jackson도 unknown 필드를 무시하지만, 전역 설정에 의존하지 않도록 DTO에 의도를 명시한다.)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AAvailabilityResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String hotelCode,
            String hotelName,
            String roomTypeCode,
            String roomTypeName,
            int maxOccupancy,
            boolean breakfastIncluded,
            String currency,
            List<DailyRate> dailyRates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyRate(LocalDate date, int remainingRooms, long nightlyRate, long taxAmount) {
    }
}
