package d6nggyun.stay.global.config;

import d6nggyun.stay.adapter.a.SupplierAAdapter;
import d6nggyun.stay.adapter.b.SupplierBAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 공급사별 WebClient를 구성하고 어댑터를 빈으로 등록한다.
 * WebClient에 base-url과 인증 헤더(X-Api-Key)를 설정하므로, 어댑터는 호출 로직에만 집중한다.
 */
@Configuration
@EnableConfigurationProperties(SupplierProperties.class)
@RequiredArgsConstructor
public class SupplierAdapterConfig {

    private final SupplierProperties properties;

    @Bean
    public SupplierAAdapter supplierAAdapter() {
        return new SupplierAAdapter(webClient(properties.a()));
    }

    @Bean
    public SupplierBAdapter supplierBAdapter() {
        return new SupplierBAdapter(webClient(properties.b()));
    }

    private WebClient webClient(SupplierProperties.Endpoint endpoint) {
        return WebClient.builder()
                .baseUrl(endpoint.baseUrl())
                .defaultHeader("X-Api-Key", endpoint.apiKey())
                .build();
    }
}
