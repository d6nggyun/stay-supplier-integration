package d6nggyun.stay.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 날짜별 잔여 객실 수. 예약 가능 객실 수는 요청 숙박일 전체의 최솟값이다.
 * 연박 전체를 예약할 수 있어야 하므로 하루라도 0이면 0이다.
 */
public record DailyAvailability(Map<LocalDate, Integer> remainingByDate) {

    public DailyAvailability {
        if (remainingByDate == null) {
            throw new IllegalArgumentException("remainingByDate는 필수입니다.");
        }
        remainingByDate = Map.copyOf(remainingByDate);
    }

    /**
     * 요청 숙박일 전체의 잔여 객실 최솟값을 반환한다.
     * 응답에 빠진 날짜는 재고를 알 수 없으므로 0(예약 불가)으로 취급한다.
     */
    public int availableRoomCount(List<LocalDate> stayDates) {
        if (stayDates.isEmpty()) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        for (LocalDate date : stayDates) {
            min = Math.min(min, remainingByDate.getOrDefault(date, 0));
        }
        return min;
    }
}
