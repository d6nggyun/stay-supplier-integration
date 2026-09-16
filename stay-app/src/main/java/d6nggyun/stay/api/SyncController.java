package d6nggyun.stay.api;

import d6nggyun.stay.application.sync.CatalogSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 숙소 목록 동기화 수동 트리거. 주기 동기화가 기본 경로이며, 이 엔드포인트는 즉시 재적재가 필요할 때의 보조 수단이다.
 * 동기화는 공급사별 실패를 격리하며 동기적으로 수행되고, 완료되면 응답한다.
 */
@RestController
@RequiredArgsConstructor
public class SyncController {

    private final CatalogSyncService catalogSyncService;

    @PostMapping("/api/v1/admin/catalog-sync")
    public ResponseEntity<Void> sync() {
        catalogSyncService.syncAll();
        return ResponseEntity.ok().build();
    }
}
