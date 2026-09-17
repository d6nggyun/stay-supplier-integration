package d6nggyun.stay.application.search;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import d6nggyun.stay.adapter.result.SupplierSearchResult;
import d6nggyun.stay.domain.SearchCriteria;
import d6nggyun.stay.domain.SupplierType;
import d6nggyun.stay.global.config.SupplierProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 청크 호출 결과 캐시. 키는 (supplier, 정렬된 코드, 검색 조건)이며, 성공 결과만 캐시한다.
 * Caffeine AsyncCache가 같은 키의 동시 미스를 한 번의 로드로 합쳐(single-flight) 스탬피드를 막고,
 * TTL에 지터를 더해 동시 만료를 분산한다. 히트면 리질리언스·공급사 호출을 건너뛴다.
 * (설계는 docs/cache.md 참고)
 */
@Component
public class ChunkResultCache {

    private final boolean enabled;
    private final long ttlNanos;
    private final long jitterNanos;
    private final AsyncCache<String, SupplierSearchResult> cache;

    public ChunkResultCache(SupplierProperties properties, MeterRegistry meterRegistry) {
        SupplierProperties.Cache cfg = properties.cache();
        this.enabled = cfg != null && cfg.enabled();
        if (enabled) {
            this.ttlNanos = Duration.ofMillis(cfg.ttlMs()).toNanos();
            this.jitterNanos = Duration.ofMillis(Math.max(0, cfg.jitterMs())).toNanos();
            this.cache = Caffeine.newBuilder()
                    .maximumSize(cfg.maximumSize())
                    .expireAfter(jitteredExpiry())
                    .recordStats()
                    .buildAsync();
            // 캐시 히트율 등 통계를 Micrometer로 노출한다.
            CaffeineCacheMetrics.monitor(meterRegistry, cache.synchronous(), "supplier.search.cache");
        } else {
            this.ttlNanos = 0;
            this.jitterNanos = 0;
            this.cache = null;
        }
    }

    /**
     * 캐시를 거쳐 청크 결과를 얻는다. 미스면 loader(리질리언스 포함 공급사 호출)를 실행해 성공 결과를 캐시한다.
     * 로드가 실패하면 Caffeine이 해당 엔트리를 남기지 않아 다음 요청에서 다시 시도된다.
     */
    public Mono<SupplierSearchResult> get(SupplierType supplier, SearchCriteria criteria, List<String> codes,
                                          Supplier<Mono<SupplierSearchResult>> loader) {
        if (!enabled) {
            return loader.get();
        }
        String key = key(supplier, criteria, codes);
        return Mono.fromFuture(() -> cache.get(key, (k, executor) -> loader.get().toFuture()));
    }

    private String key(SupplierType supplier, SearchCriteria criteria, List<String> codes) {
        String sortedCodes = String.join(",", codes.stream().sorted().toList());
        return supplier.name() + '|' + sortedCodes + '|'
                + criteria.checkIn() + '|' + criteria.checkOut() + '|'
                + criteria.adults() + '|' + criteria.children();
    }

    /**
     * TTL에 무작위 지터를 더한 만료. 읽기/갱신으로는 수명을 연장하지 않는다(expireAfterWrite + 지터).
     * 요금·재고는 변동 데이터라, 읽기로 연장하면 인기 질의일수록 stale 값을 계속 서빙하게 된다.
     * TTL은 "가져온 시점 기준 최대 stale 허용치"여야 하므로 읽기로 연장하지 않는다. (근거는 docs/cache.md)
     */
    private Expiry<String, SupplierSearchResult> jitteredExpiry() {
        return new Expiry<>() {
            @Override
            public long expireAfterCreate(String key, SupplierSearchResult value, long currentTime) {
                long jitter = jitterNanos == 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterNanos + 1);
                return ttlNanos + jitter;
            }

            @Override
            public long expireAfterUpdate(String key, SupplierSearchResult value, long currentTime, long currentDuration) {
                return currentDuration;
            }

            @Override
            public long expireAfterRead(String key, SupplierSearchResult value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };
    }
}
