package d6nggyun.stay.adapter.result;

import d6nggyun.stay.domain.DailyAvailability;
import d6nggyun.stay.domain.Price;
import d6nggyun.stay.domain.SupplierType;

/**
 * 공급사 응답을 표준 모델로 정규화한 결과. 아직 내부 식별자로 치환되기 전이므로 공급사 코드를 유지한다.
 * 오케스트레이터가 매핑으로 공급사 코드를 내부 식별자로 바꿔 최종 {@code StayOffer}를 만든다.
 */
public record SupplierOffer(
        SupplierType supplier,
        String supplierStayCode,
        String stayName,
        String supplierRoomTypeCode,
        String roomTypeName,
        int maxOccupancy,
        int availableRoomCount,
        boolean breakfastIncluded,
        Price price,
        DailyAvailability dailyAvailability) {
}
