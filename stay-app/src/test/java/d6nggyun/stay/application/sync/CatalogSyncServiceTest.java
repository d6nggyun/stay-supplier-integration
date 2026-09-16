package d6nggyun.stay.application.sync;

import d6nggyun.stay.adapter.SupplierAdapter;
import d6nggyun.stay.adapter.result.SupplierCatalog;
import d6nggyun.stay.global.config.SupplierProperties;
import d6nggyun.stay.global.exception.SupplierFailureKind;
import d6nggyun.stay.global.exception.SupplierIntegrationException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static d6nggyun.stay.domain.SupplierType.SUPPLIER_A;
import static d6nggyun.stay.domain.SupplierType.SUPPLIER_B;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSyncServiceTest {

    @Test
    void 한_공급사_조회가_실패해도_나머지는_upsert한다() {
        // A는 실패, B는 성공
        SupplierAdapter failingA = mock(SupplierAdapter.class);
        when(failingA.supplier()).thenReturn(SUPPLIER_A);
        when(failingA.fetchCatalog()).thenReturn(Mono.error(
                new SupplierIntegrationException(SUPPLIER_A, SupplierFailureKind.HTTP_ERROR, "boom")));

        SupplierCatalog catalogB = new SupplierCatalog(SUPPLIER_B, List.of(
                new SupplierCatalog.Stay("B77120", "Riverside Hotel Seoul", List.of(
                        new SupplierCatalog.RoomType("R-401", "Deluxe Twin Room", 2)))));
        SupplierAdapter okB = mock(SupplierAdapter.class);
        when(okB.supplier()).thenReturn(SUPPLIER_B);
        when(okB.fetchCatalog()).thenReturn(Mono.just(catalogB));

        MappingUpserter upserter = mock(MappingUpserter.class);
        SupplierProperties properties = new SupplierProperties(
                null, null, new SupplierProperties.Sync(3_600_000, true));
        CatalogSyncService service = new CatalogSyncService(List.of(failingA, okB), upserter, properties);

        service.syncAll();

        // 실패한 A는 upsert되지 않고, 성공한 B만 upsert된다.
        verify(upserter, times(1)).upsert(catalogB);
        verify(upserter, never()).upsert(org.mockito.ArgumentMatchers.argThat(
                catalog -> catalog.supplier() == SUPPLIER_A));
    }
}
