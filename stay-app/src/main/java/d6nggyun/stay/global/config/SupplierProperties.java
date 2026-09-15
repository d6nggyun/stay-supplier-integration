package d6nggyun.stay.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공급사 연동 설정. base-url·api-key를 코드에서 분리하고, 동기화 주기·기동 동기화 여부를 설정값으로 둔다.
 */
@ConfigurationProperties(prefix = "supplier")
public record SupplierProperties(Endpoint a, Endpoint b, Sync sync) {

    public record Endpoint(String baseUrl, String apiKey) {
    }

    public record Sync(long intervalMs, boolean enabled) {
    }
}
