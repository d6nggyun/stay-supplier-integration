package d6nggyun.stay.api;

import d6nggyun.stay.application.search.SearchResult;
import d6nggyun.stay.application.search.StaySearchService;
import d6nggyun.stay.application.search.SupplierResult;
import d6nggyun.stay.application.search.SupplierSearchStatus;
import d6nggyun.stay.domain.DailyAvailability;
import d6nggyun.stay.domain.Price;
import d6nggyun.stay.domain.StayOffer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static d6nggyun.stay.domain.SupplierType.SUPPLIER_A;
import static d6nggyun.stay.domain.SupplierType.SUPPLIER_B;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaySearchController.class)
class StaySearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaySearchService staySearchService;

    @Test
    void 검색_성공이면_200과_결과_공급사_상태를_반환하고_내부전용_필드는_노출하지_않는다() throws Exception {
        when(staySearchService.search(any())).thenReturn(new SearchResult(List.of(
                SupplierResult.success(SUPPLIER_A, 180, List.of(stayOffer(1L, 10L))),
                SupplierResult.skipped(SUPPLIER_B))));

        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "2026-09-01")
                        .param("checkOut", "2026-09-04")
                        .param("adults", "2")
                        .param("children", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].stayId").value(1))
                .andExpect(jsonPath("$.results[0].roomTypeId").value(10))
                .andExpect(jsonPath("$.results[0].supplier").value("SUPPLIER_A"))
                .andExpect(jsonPath("$.results[0].price.grossTotalAmount").value(300000))
                // 내부 전용 dailyAvailability는 응답에 없어야 한다.
                .andExpect(jsonPath("$.results[0].dailyAvailability").doesNotExist())
                .andExpect(jsonPath("$.suppliers[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.suppliers[0].resultCount").value(1))
                .andExpect(jsonPath("$.suppliers[1].status").value("SKIPPED"));
    }

    @Test
    void 체크인이_체크아웃보다_뒤면_400() throws Exception {
        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "2026-09-05")
                        .param("checkOut", "2026-09-04")
                        .param("adults", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 필수_파라미터_누락이면_400() throws Exception {
        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "2026-09-01")
                        .param("checkOut", "2026-09-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }

    @Test
    void 날짜_형식이_잘못되면_400() throws Exception {
        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "not-a-date")
                        .param("checkOut", "2026-09-04")
                        .param("adults", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void 호출한_공급사가_전부_실패면_502이고_본문에_상태를_담는다() throws Exception {
        when(staySearchService.search(any())).thenReturn(new SearchResult(List.of(
                SupplierResult.failure(SUPPLIER_A, SupplierSearchStatus.HTTP_ERROR, 100, "HTTP_ERROR", "boom"),
                SupplierResult.failure(SUPPLIER_B, SupplierSearchStatus.PROTOCOL_ERROR, 100, "PROTOCOL_ERROR", "bad"))));

        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "2026-09-01")
                        .param("checkOut", "2026-09-04")
                        .param("adults", "2"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.results").isEmpty())
                .andExpect(jsonPath("$.suppliers[0].status").value("HTTP_ERROR"));
    }

    @Test
    void 호출_대상이_하나도_없으면_503() throws Exception {
        when(staySearchService.search(any())).thenReturn(new SearchResult(List.of(
                SupplierResult.skipped(SUPPLIER_A),
                SupplierResult.skipped(SUPPLIER_B))));

        mockMvc.perform(get("/api/v1/stays/search")
                        .param("checkIn", "2026-09-01")
                        .param("checkOut", "2026-09-04")
                        .param("adults", "2"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.suppliers[0].status").value("SKIPPED"));
    }

    private StayOffer stayOffer(long stayId, long roomTypeId) {
        Price price = Price.of("KRW", 300_000, 3);
        DailyAvailability availability = new DailyAvailability(Map.of());
        return new StayOffer(stayId, "Stay", roomTypeId, "Room", 2, 1,
                SUPPLIER_A, false, price, availability);
    }
}
