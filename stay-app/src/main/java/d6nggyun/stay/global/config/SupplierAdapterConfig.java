package d6nggyun.stay.global.config;

import d6nggyun.stay.adapter.a.SupplierAAdapter;
import d6nggyun.stay.adapter.b.SupplierBAdapter;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 공급사별 WebClient를 구성하고 어댑터를 빈으로 등록한다.
 * WebClient에 base-url과 인증 헤더(X-Api-Key)를 설정하므로, 어댑터는 호출 로직에만 집중한다.
 */
@Configuration
@EnableConfigurationProperties(SupplierProperties.class)
@RequiredArgsConstructor
public class SupplierAdapterConfig {

    /**
     * 응답 본문을 메모리로 버퍼링하는 상한. 기본값(256KB)은 청크(최대 50개 숙소)의 재고·요금 응답이 커지면
     * 초과해 디코딩이 실패할 수 있어, 여유를 둬 4MB로 상향한다.
     */
    private static final int MAX_RESPONSE_IN_MEMORY_BYTES = 4 * 1024 * 1024;

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
        // 연결 타임아웃은 소켓을 여는 단계의 값이라 WebClient 요청 API가 아니라 전송 엔진(Reactor Netty HttpClient)의
        // 채널 옵션으로만 설정할 수 있다. 그래서 CONNECT_TIMEOUT_MILLIS를 건 HttpClient를 만들고,
        // 이를 커넥터(WebClient ↔ 전송 엔진 연결부)로 끼운다.
        // (응답 타임아웃·전체 예산은 "호출 하나가 얼마나 기다릴지"라 오케스트레이터가 .timeout()으로 적용해
        //  모든 타임아웃을 같은 TIMEOUT 상태로 통일한다. — StaySearchService 참고)
        int connectTimeoutMs = (int) properties.search().connectTimeoutMs();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);
        return WebClient.builder()
                .baseUrl(endpoint.baseUrl())
                .defaultHeader("X-Api-Key", endpoint.apiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 응답 버퍼 상한을 기본 256KB에서 상향해, 청크 응답이 커져도 디코딩이 끊기지 않게 한다.
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_IN_MEMORY_BYTES))
                .build();
    }
}
