package d6nggyun.stay.api.dto;

import d6nggyun.stay.domain.StayOffer;

/**
 * 검색 결과 항목. 식별자는 공급사 코드가 아닌 내부 식별자로 노출하며, 내부 전용인 dailyAvailability는 응답에 담지 않는다.
 */
public record StayOfferResponse(
        long stayId,
        String stayName,
        long roomTypeId,
        String roomTypeName,
        int maxOccupancy,
        int availableRoomCount,
        String supplier,
        boolean breakfastIncluded,
        PriceResponse price) {

    public static StayOfferResponse from(StayOffer offer) {
        return new StayOfferResponse(
                offer.internalStayId(),
                offer.stayName(),
                offer.internalRoomTypeId(),
                offer.roomTypeName(),
                offer.maxOccupancy(),
                offer.availableRoomCount(),
                offer.supplier().name(),
                offer.breakfastIncluded(),
                PriceResponse.from(offer.price()));
    }
}
