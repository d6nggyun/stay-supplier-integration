package d6nggyun.stay.api;

import d6nggyun.stay.application.sync.CatalogSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 숙소 목록 동기화 수동 트리거. 주기 동기화가 기본 경로이며, 이 엔드포인트는 즉시 재적재가 필요할 때의 보조 수단이다.
 * 동기화는 공급사별 실패를 격리하며 동기적으로 수행되고, 완료되면 응답한다.
 */
@Tag(name = "관리", description = "운영 보조 기능")
@RestController
@RequiredArgsConstructor
public class SyncController {

    private final CatalogSyncService catalogSyncService;

    @Operation(summary = "숙소 목록 수동 동기화",
            description = "공급사 카탈로그를 즉시 재적재합니다. 주기 동기화의 보조 수단이며 공급사별 실패를 격리합니다.")
    @PostMapping("/api/v1/admin/catalog-sync")
    public ResponseEntity<Void> sync() {
        catalogSyncService.syncAll();
        return ResponseEntity.ok().build();
    }
}
