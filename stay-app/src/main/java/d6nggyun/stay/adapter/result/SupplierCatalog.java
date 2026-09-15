package d6nggyun.stay.adapter.result;

import d6nggyun.stay.domain.SupplierType;

import java.util.List;

/**
 * 공급사 숙소 목록(①)을 표준 형태로 정규화한 결과. 숙소 목록 동기화가 이 값으로 매핑을 만든다.
 */
public record SupplierCatalog(SupplierType supplier, List<Stay> stays) {

    public record Stay(String supplierStayCode, String stayName, List<RoomType> roomTypes) {
    }

    public record RoomType(String supplierRoomTypeCode, String roomTypeName, int maxOccupancy) {
    }
}
