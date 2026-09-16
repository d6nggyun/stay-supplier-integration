package d6nggyun.stay.application.search;

import d6nggyun.stay.adapter.SupplierAdapter;
import d6nggyun.stay.adapter.result.SupplierOffer;
import d6nggyun.stay.adapter.result.SupplierSearchResult;
import d6nggyun.stay.domain.DailyAvailability;
import d6nggyun.stay.domain.Price;
import d6nggyun.stay.domain.SearchCriteria;
import d6nggyun.stay.domain.StayOffer;
import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.global.exception.SupplierFailureKind;
import d6nggyun.stay.global.exception.SupplierIntegrationException;
import d6nggyun.stay.infrastructure.persistence.entity.RoomTypeMapping;
import d6nggyun.stay.infrastructure.persistence.entity.StayMapping;
import d6nggyun.stay.infrastructure.persistence.repository.RoomTypeMappingRepository;
import d6nggyun.stay.infrastructure.persistence.repository.StayMappingRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static d6nggyun.stay.domain.SupplierType.SUPPLIER_A;
import static d6nggyun.stay.domain.SupplierType.SUPPLIER_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaySearchServiceTest {

    private final SearchCriteria criteria = new SearchCriteria(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

    @Test
    void 두_공급사_결과를_병합하고_공급사_코드를_내부_식별자로_치환한다() {
        SupplierAdapter adapterA = mock(SupplierAdapter.class);
        when(adapterA.supplier()).thenReturn(SUPPLIER_A);
        when(adapterA.search(eq(criteria), eq(List.of("H1")))).thenReturn(Mono.just(
                new SupplierSearchResult(SUPPLIER_A, List.of(
                        offer(SUPPLIER_A, "H1", "Stay A", "R1", "Room A")))));

        SupplierAdapter adapterB = mock(SupplierAdapter.class);
        when(adapterB.supplier()).thenReturn(SUPPLIER_B);
        when(adapterB.search(eq(criteria), eq(List.of("P1")))).thenReturn(Mono.just(
                new SupplierSearchResult(SUPPLIER_B, List.of(
                        offer(SUPPLIER_B, "P1", "Stay B", "RB1", "Room B")))));

        StaySearchService service = service(
                List.of(adapterA, adapterB),
                List.of(stayMapping(SUPPLIER_A, "H1", 10L), stayMapping(SUPPLIER_B, "P1", 20L)),
                List.of(roomTypeMapping(SUPPLIER_A, "H1", "R1", 100L),
                        roomTypeMapping(SUPPLIER_B, "P1", "RB1", 200L)));

        SearchResult result = service.search(criteria);

        // 결과는 내부 식별자로 치환되고, 공급사 순서(A→B)로 안정 정렬된다.
        List<StayOffer> offers = result.results();
        assertThat(offers).extracting(StayOffer::supplier, StayOffer::internalStayId, StayOffer::internalRoomTypeId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(SUPPLIER_A, 10L, 100L),
                        org.assertj.core.groups.Tuple.tuple(SUPPLIER_B, 20L, 200L));
        assertThat(result.suppliers()).extracting(SupplierResult::supplier, SupplierResult::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(SUPPLIER_A, SupplierSearchStatus.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple(SUPPLIER_B, SupplierSearchStatus.SUCCESS));
    }

    @Test
    void 매핑이_없는_공급사는_호출하지_않고_SKIPPED로_표기한다() {
        SupplierAdapter adapterA = mock(SupplierAdapter.class);
        when(adapterA.supplier()).thenReturn(SUPPLIER_A);
        when(adapterA.search(eq(criteria), eq(List.of("H1")))).thenReturn(Mono.just(
                new SupplierSearchResult(SUPPLIER_A, List.of(
                        offer(SUPPLIER_A, "H1", "Stay A", "R1", "Room A")))));

        SupplierAdapter adapterB = mock(SupplierAdapter.class);
        when(adapterB.supplier()).thenReturn(SUPPLIER_B);

        // A 매핑만 존재 → B는 호출 대상에서 빠진다.
        StaySearchService service = service(
                List.of(adapterA, adapterB),
                List.of(stayMapping(SUPPLIER_A, "H1", 10L)),
                List.of(roomTypeMapping(SUPPLIER_A, "H1", "R1", 100L)));

        SearchResult result = service.search(criteria);

        verify(adapterB, never()).search(any(), anyList());
        assertThat(result.suppliers()).extracting(SupplierResult::supplier, SupplierResult::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(SUPPLIER_A, SupplierSearchStatus.SUCCESS),
                        org.assertj.core.groups.Tuple.tuple(SUPPLIER_B, SupplierSearchStatus.SKIPPED));
    }

    @Test
    void 한_공급사가_실패해도_나머지_공급사_결과로_응답한다() {
        SupplierAdapter adapterA = mock(SupplierAdapter.class);
        when(adapterA.supplier()).thenReturn(SUPPLIER_A);
        when(adapterA.search(eq(criteria), eq(List.of("H1")))).thenReturn(Mono.error(
                new SupplierIntegrationException(SUPPLIER_A, SupplierFailureKind.HTTP_ERROR, "Supplier A HTTP 503")));

        SupplierAdapter adapterB = mock(SupplierAdapter.class);
        when(adapterB.supplier()).thenReturn(SUPPLIER_B);
        when(adapterB.search(eq(criteria), eq(List.of("P1")))).thenReturn(Mono.just(
                new SupplierSearchResult(SUPPLIER_B, List.of(
                        offer(SUPPLIER_B, "P1", "Stay B", "RB1", "Room B")))));

        StaySearchService service = service(
                List.of(adapterA, adapterB),
                List.of(stayMapping(SUPPLIER_A, "H1", 10L), stayMapping(SUPPLIER_B, "P1", 20L)),
                List.of(roomTypeMapping(SUPPLIER_A, "H1", "R1", 100L),
                        roomTypeMapping(SUPPLIER_B, "P1", "RB1", 200L)));

        SearchResult result = service.search(criteria);

        // A는 실패 상태로 남고, B의 결과는 그대로 반환된다.
        assertThat(result.results()).extracting(StayOffer::supplier).containsExactly(SUPPLIER_B);
        SupplierResult a = result.suppliers().get(0);
        assertThat(a.supplier()).isEqualTo(SUPPLIER_A);
        assertThat(a.status()).isEqualTo(SupplierSearchStatus.HTTP_ERROR);
        assertThat(a.errorCode()).isEqualTo("HTTP_ERROR");
        assertThat(result.suppliers().get(1).status()).isEqualTo(SupplierSearchStatus.SUCCESS);
    }

    private StaySearchService service(List<SupplierAdapter> adapters,
                                     List<StayMapping> stayMappings, List<RoomTypeMapping> roomTypeMappings) {
        StayMappingRepository stayRepo = mock(StayMappingRepository.class);
        when(stayRepo.findByActiveTrue()).thenReturn(stayMappings);
        RoomTypeMappingRepository roomTypeRepo = mock(RoomTypeMappingRepository.class);
        when(roomTypeRepo.findByActiveTrue()).thenReturn(roomTypeMappings);
        return new StaySearchService(adapters, stayRepo, roomTypeRepo);
    }

    private SupplierOffer offer(SupplierType supplier, String stayCode, String stayName,
                                String roomTypeCode, String roomTypeName) {
        Price price = Price.of("KRW", 300_000, criteria.nights());
        DailyAvailability availability = new DailyAvailability(Map.of(
                LocalDate.of(2026, 9, 1), 3,
                LocalDate.of(2026, 9, 2), 2,
                LocalDate.of(2026, 9, 3), 1));
        int available = availability.availableRoomCount(criteria.stayDates());
        return new SupplierOffer(supplier, stayCode, stayName, roomTypeCode, roomTypeName,
                2, available, false, price, availability);
    }

    private StayMapping stayMapping(SupplierType supplier, String stayCode, long internalStayId) {
        StayMapping mapping = mock(StayMapping.class);
        when(mapping.getSupplier()).thenReturn(supplier);
        when(mapping.getSupplierStayCode()).thenReturn(stayCode);
        when(mapping.getInternalStayId()).thenReturn(internalStayId);
        return mapping;
    }

    private RoomTypeMapping roomTypeMapping(SupplierType supplier, String stayCode,
                                           String roomTypeCode, long internalRoomTypeId) {
        RoomTypeMapping mapping = mock(RoomTypeMapping.class);
        when(mapping.getSupplier()).thenReturn(supplier);
        when(mapping.getSupplierStayCode()).thenReturn(stayCode);
        when(mapping.getSupplierRoomTypeCode()).thenReturn(roomTypeCode);
        when(mapping.getInternalRoomTypeId()).thenReturn(internalRoomTypeId);
        return mapping;
    }
}
