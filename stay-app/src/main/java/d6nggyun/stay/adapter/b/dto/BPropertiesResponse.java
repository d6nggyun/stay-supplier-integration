package d6nggyun.stay.adapter.b.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET /b/api/properties — Supplier B 숙소 목록(①) 응답. 어댑터 계층 내부 전용 DTO.
 * B는 항상 HTTP 200을 주고 본문 {@code resultCode}로 성공/실패를 표현하며, 결과는 {@code data.items}로 감싸진다.
 */
// ignoreUnknown = true: 모르는 필드는 무시한다. 공급사가 응답에 필드를 추가해도 역직렬화가 깨지지 않게 하려는 것.
@JsonIgnoreProperties(ignoreUnknown = true)
public record BPropertiesResponse(String resultCode, String resultMessage, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<Property> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Property(String propertyId, String propertyName, List<Room> rooms) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Room(String roomId, String roomName, int maxOccupancy) {
    }
}
