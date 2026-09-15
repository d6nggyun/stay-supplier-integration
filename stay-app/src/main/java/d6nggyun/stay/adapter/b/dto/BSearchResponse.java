package d6nggyun.stay.adapter.b.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /b/api/search — Supplier B 재고·요금(②) 응답. 어댑터 계층 내부 전용 DTO.
 * B는 세금 포함 숙박 전체 총액({@code totalPrice})을 주고 날짜별 요금은 제공하지 않는다.
 */
// ignoreUnknown = true: 모르는 필드는 무시한다. 공급사가 응답에 필드를 추가해도 역직렬화가 깨지지 않게 하려는 것.
@JsonIgnoreProperties(ignoreUnknown = true)
public record BSearchResponse(String resultCode, String resultMessage, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String propertyId,
            String propertyName,
            String roomId,
            String roomName,
            int maxOccupancy,
            boolean breakfastIncluded,
            String currency,
            long totalPrice,
            boolean taxIncluded,
            List<Inventory> inventory) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Inventory(LocalDate date, int remainingRooms) {
    }
}
