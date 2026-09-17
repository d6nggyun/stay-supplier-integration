package d6nggyun.stay.global.config;

import d6nggyun.stay.global.exception.SupplierIntegrationException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * 재시도·서킷 브레이커 인스턴스를 설정값(supplier.resilience.*)에서 구성한다.
 * 재시도는 두 공급사 공통 정책이라 하나를 공유하고, 서킷은 공급사별 독립 상태가 필요해 레지스트리로 둔다.
 * 레지스트리로 두면 Micrometer 지표 바인딩도 함께 얻는다(서킷·재시도 지표 자동 등록).
 * (오케스트레이터가 공급사명으로 인스턴스를 받아 청크 호출에 얹는다. — StaySearchService 참고)
 */
@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {

    private static final String SEARCH_RETRY_NAME = "supplierSearch";

    private final SupplierProperties properties;

    @Bean
    public RetryRegistry supplierRetryRegistry() {
        SupplierProperties.Resilience.Retry cfg = properties.resilience().retry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(cfg.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        Duration.ofMillis(cfg.waitDurationMs()), cfg.backoffMultiplier()))
                // 일시적 실패만 재시도: 타임아웃, 또는 재시도 가능으로 표시된 연동 실패(5xx·연결 실패).
                .retryOnException(this::isRetryable)
                .build();
        return RetryRegistry.of(config);
    }

    @Bean
    public Retry supplierSearchRetry(RetryRegistry supplierRetryRegistry) {
        return supplierRetryRegistry.retry(SEARCH_RETRY_NAME);
    }

    @Bean
    public CircuitBreakerRegistry supplierCircuitBreakerRegistry() {
        SupplierProperties.Resilience.CircuitBreaker cfg = properties.resilience().circuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cfg.failureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cfg.slidingWindowSize())
                .minimumNumberOfCalls(cfg.minimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofMillis(cfg.waitDurationInOpenMs()))
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    /** 재시도 지표(Micrometer)를 레지스트리에서 바인딩한다. MeterBinder 빈이라 MeterRegistry에 자동 등록된다. */
    @Bean
    public TaggedRetryMetrics retryMetrics(RetryRegistry supplierRetryRegistry) {
        return TaggedRetryMetrics.ofRetryRegistry(supplierRetryRegistry);
    }

    /** 서킷 브레이커 지표(Micrometer)를 레지스트리에서 바인딩한다. */
    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry supplierCircuitBreakerRegistry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(supplierCircuitBreakerRegistry);
    }

    private boolean isRetryable(Throwable throwable) {
        return throwable instanceof TimeoutException
                || (throwable instanceof SupplierIntegrationException integration && integration.isRetryable());
    }
}
