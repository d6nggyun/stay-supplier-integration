package d6nggyun.stay.adapter.a.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET /a/v1/hotels — Supplier A 숙소 목록(①) 응답. 어댑터 계층 내부 전용 DTO.
 */
// ignoreUnknown = true: 모르는 필드는 무시한다. 공급사가 응답에 필드를 추가해도 역직렬화가 깨지지 않게 하려는 것.
// (Spring 기본 Jackson도 unknown 필드를 무시하지만, 전역 설정에 의존하지 않도록 DTO에 의도를 명시한다.)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AHotelsResponse(List<Hotel> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hotel(String hotelCode, String hotelName, List<RoomType> roomTypes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoomType(String roomTypeCode, String roomTypeName, int maxOccupancy) {
    }
}
