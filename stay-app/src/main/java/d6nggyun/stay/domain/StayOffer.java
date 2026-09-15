package d6nggyun.stay.domain;

/**
 * 표준 숙박 상품. 공급사 API 필드명과 무관한 내부 표준 모델이며,
 * 식별자는 공급사 코드가 아닌 내부 식별자를 사용한다.
 * dailyAvailability는 내부 모델에만 보존하고 검색 목록 응답에서는 직접 노출하지 않는다.
 */
public record StayOffer(
        long internalStayId,
        String stayName,
        long internalRoomTypeId,
        String roomTypeName,
        int maxOccupancy,
        int availableRoomCount,
        SupplierType supplier,
        boolean breakfastIncluded,
        Price price,
        DailyAvailability dailyAvailability) {
}
