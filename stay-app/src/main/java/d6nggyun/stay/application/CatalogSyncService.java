package d6nggyun.stay.application;

import d6nggyun.stay.adapter.SupplierAdapter;
import d6nggyun.stay.adapter.result.SupplierCatalog;
import d6nggyun.stay.global.config.SupplierProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 숙소 목록 동기화. 두 어댑터의 카탈로그를 받아 매핑을 upsert한다.
 *
 * 동기화 시점: 주기 스케줄러 하나가 담당한다. initialDelay가 없어 첫 실행이 기동 직후 발생하므로,
 * 그 첫 실행이 초기 적재를 겸하고 이후에는 설정 주기로 반복된다. 수동 트리거는 syncAll()로 노출한다.
 * 숙소 목록은 정적이라 검색마다 호출하지 않는다.
 *
 * 실패 격리: 한 공급사 조회가 실패해도 전체를 중단하지 않고 경고 로그만 남긴 뒤 나머지 공급사를 계속 처리한다.
 * 기존 매핑은 유지되며 복구는 다음 주기 동기화가 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogSyncService {

    private final List<SupplierAdapter> adapters;
    private final MappingUpserter mappingUpserter;
    private final SupplierProperties properties;

    /** 수동 트리거를 포함한 동기화 진입점. */
    public void syncAll() {
        for (SupplierAdapter adapter : adapters) {
            syncOne(adapter);
        }
    }

    private void syncOne(SupplierAdapter adapter) {
        try {
            SupplierCatalog catalog = adapter.fetchCatalog().block();  // 네트워크 호출은 트랜잭션 밖에서
            mappingUpserter.upsert(catalog);
            log.info("숙소 목록 동기화 완료: supplier={}, stays={}", adapter.supplier(), catalog.stays().size());
        } catch (Exception e) {
            // 한 공급사 실패가 전체 동기화를 막지 않는다. 기존 매핑은 유지하고 다음 주기에 복구한다.
            log.warn("숙소 목록 동기화 실패: supplier={}, 기존 매핑 유지", adapter.supplier(), e);
        }
    }

    /**
     * 주기 동기화. initialDelay가 없으므로 첫 실행이 기동 직후 발생해 초기 적재를 겸한다.
     * enabled=false면 아무 것도 하지 않는다(테스트 등에서 외부 호출을 막기 위함).
     */
    @Scheduled(fixedDelayString = "${supplier.sync.interval-ms}")
    public void scheduledSync() {
        if (!properties.sync().enabled()) {
            return;
        }
        syncAll();
    }
}
