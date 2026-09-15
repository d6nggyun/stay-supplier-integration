package d6nggyun.stay.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DailyAvailabilityTest {

    private static final LocalDate D1 = LocalDate.parse("2026-09-01");
    private static final LocalDate D2 = LocalDate.parse("2026-09-02");
    private static final LocalDate D3 = LocalDate.parse("2026-09-03");

    @Test
    void 예약_가능_객실_수는_날짜별_재고의_최솟값이다() {
        DailyAvailability availability = new DailyAvailability(Map.of(D1, 3, D2, 1, D3, 5));

        assertThat(availability.availableRoomCount(List.of(D1, D2, D3))).isEqualTo(1);
    }

    @Test
    void 하루라도_재고가_0이면_예약_가능_객실_수는_0이다() {
        DailyAvailability availability = new DailyAvailability(Map.of(D1, 2, D2, 0, D3, 4));

        assertThat(availability.availableRoomCount(List.of(D1, D2, D3))).isEqualTo(0);
    }

    @Test
    void 요청_숙박일_중_응답에_빠진_날짜가_있으면_재고를_0으로_취급한다() {
        DailyAvailability availability = new DailyAvailability(Map.of(D1, 2, D3, 4)); // D2 누락

        assertThat(availability.availableRoomCount(List.of(D1, D2, D3))).isEqualTo(0);
    }
}
