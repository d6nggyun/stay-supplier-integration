package d6nggyun.stay.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공급사 연동 설정. base-url·api-key를 코드에서 분리하고, 동기화 주기·기동 동기화 여부와
 * 검색의 타임아웃·청크 파라미터를 설정값으로 둔다.
 */
@ConfigurationProperties(prefix = "supplier")
public record SupplierProperties(Endpoint a, Endpoint b, Sync sync, Search search) {

    public record Endpoint(String baseUrl, String apiKey) {
    }

    public record Sync(long intervalMs, boolean enabled) {
    }

    /**
     * 검색 견고성 파라미터.
     * - connectTimeoutMs: 연결 타임아웃(커넥터 레벨)
     * - responseTimeoutMs: 응답 타임아웃(청크 호출 하나당)
     * - requestBudgetMs: 전체 요청 예산(공급사 하나당 상한, 병렬이라 벽시계는 예산에 수렴)
     * - chunkSize: 재고·요금 조회 1회 최대 숙소 코드 수(공급사 규약상 최대 50)
     * - chunkConcurrency: 청크 동시 호출 상한(Rate Limit 초과 방지)
     */
    public record Search(long connectTimeoutMs, long responseTimeoutMs, long requestBudgetMs,
                         int chunkSize, int chunkConcurrency) {
    }
}
