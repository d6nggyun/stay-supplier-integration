package d6nggyun.stay.application.search;

import d6nggyun.stay.adapter.SupplierAdapter;
import d6nggyun.stay.adapter.result.SupplierOffer;
import d6nggyun.stay.adapter.result.SupplierSearchResult;
import d6nggyun.stay.domain.DailyAvailability;
import d6nggyun.stay.domain.Price;
import d6nggyun.stay.domain.SearchCriteria;
import d6nggyun.stay.domain.StayOffer;
import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.global.config.SupplierProperties;
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

    @Test
    void 응답이_지연되면_응답_타임아웃으로_TIMEOUT_처리한다() {
        SupplierAdapter adapterA = mock(SupplierAdapter.class);
        when(adapterA.supplier()).thenReturn(SUPPLIER_A);
        when(adapterA.search(eq(criteria), eq(List.of("H1")))).thenReturn(Mono.never()); // 응답 없음

        SupplierAdapter adapterB = mock(SupplierAdapter.class);
        when(adapterB.supplier()).thenReturn(SUPPLIER_B);
        when(adapterB.search(eq(criteria), eq(List.of("P1")))).thenReturn(Mono.just(
                new SupplierSearchResult(SUPPLIER_B, List.of(
                        offer(SUPPLIER_B, "P1", "Stay B", "RB1", "Room B")))));

        // 응답 타임아웃 100ms로 줄여 A는 시간 안에 응답하지 못하게 한다.
        StaySearchService service = service(
                List.of(adapterA, adapterB),
                List.of(stayMapping(SUPPLIER_A, "H1", 10L), stayMapping(SUPPLIER_B, "P1", 20L)),
                List.of(roomTypeMapping(SUPPLIER_A, "H1", "R1", 100L),
                        roomTypeMapping(SUPPLIER_B, "P1", "RB1", 200L)),
                searchConfig(100, 6_000, 50, 4));

        SearchResult result = service.search(criteria);

        assertThat(result.results()).extracting(StayOffer::supplier).containsExactly(SUPPLIER_B);
        assertThat(result.suppliers().get(0).status()).isEqualTo(SupplierSearchStatus.TIMEOUT);
        assertThat(result.suppliers().get(1).status()).isEqualTo(SupplierSearchStatus.SUCCESS);
    }

    @Test
    void 청크가_나뉘고_일부만_실패하면_성공_청크는_유지하고_PARTIAL_SUCCESS로_표기한다() {
        SupplierAdapter adapterA = mock(SupplierAdapter.class);
        when(adapterA.supplier()).thenReturn(SUPPLIER_A);
        // chunk-size 2 → [H1,H2] 성공, [H3] 실패
        when(adapterA.search(eq(criteria), eq(List.of("H1", "H2")))).thenReturn(Mono.just(
                new SupplierSearchResult(SUPPLIER_A, List.of(
                        offer(SUPPLIER_A, "H1", "Stay A1", "R1", "Room A1"),
                        offer(SUPPLIER_A, "H2", "Stay A2", "R2", "Room A2")))));
        when(adapterA.search(eq(criteria), eq(List.of("H3")))).thenReturn(Mono.error(
                new SupplierIntegrationException(SUPPLIER_A, SupplierFailureKind.HTTP_ERROR, "Supplier A HTTP 503")));

        StaySearchService service = service(
                List.of(adapterA),
                List.of(stayMapping(SUPPLIER_A, "H1", 10L), stayMapping(SUPPLIER_A, "H2", 11L),
                        stayMapping(SUPPLIER_A, "H3", 12L)),
                List.of(roomTypeMapping(SUPPLIER_A, "H1", "R1", 100L),
                        roomTypeMapping(SUPPLIER_A, "H2", "R2", 101L)),
                searchConfig(3_000, 6_000, 2, 4));

        SearchResult result = service.search(criteria);

        SupplierResult a = result.suppliers().get(0);
        assertThat(a.status()).isEqualTo(SupplierSearchStatus.PARTIAL_SUCCESS);
        assertThat(a.errorCode()).isEqualTo("HTTP_ERROR");
        // 성공 청크의 offer(H1,H2)는 유지된다.
        assertThat(result.results()).extracting(StayOffer::internalStayId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    private StaySearchService service(List<SupplierAdapter> adapters,
                                     List<StayMapping> stayMappings, List<RoomTypeMapping> roomTypeMappings) {
        // 기본값: 타임아웃은 넉넉하게(타임아웃 경로를 타지 않도록), 단일 청크(size 50).
        return service(adapters, stayMappings, roomTypeMappings, searchConfig(3_000, 6_000, 50, 4));
    }

    private StaySearchService service(List<SupplierAdapter> adapters, List<StayMapping> stayMappings,
                                     List<RoomTypeMapping> roomTypeMappings, SupplierProperties.Search search) {
        StayMappingRepository stayRepo = mock(StayMappingRepository.class);
        when(stayRepo.findAll()).thenReturn(stayMappings);
        RoomTypeMappingRepository roomTypeRepo = mock(RoomTypeMappingRepository.class);
        when(roomTypeRepo.findAll()).thenReturn(roomTypeMappings);
        SupplierProperties properties = new SupplierProperties(null, null, null, search);
        return new StaySearchService(adapters, stayRepo, roomTypeRepo, properties);
    }

    private SupplierProperties.Search searchConfig(long responseMs, long budgetMs, int chunkSize, int concurrency) {
        return new SupplierProperties.Search(1_000, responseMs, budgetMs, chunkSize, concurrency);
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
