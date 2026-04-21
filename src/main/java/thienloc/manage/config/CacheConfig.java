package thienloc.manage.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String EFF_MULTIPLIERS  = "eff-multipliers";
    public static final String EFF_RATES        = "eff-rates";
    public static final String EFF_RATE_SECS    = "eff-rate-secs";
    public static final String MASTERDB_MONTHS  = "masterdb-months";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cm = new SimpleCacheManager();
        cm.setCaches(List.of(
            build(EFF_MULTIPLIERS, 60),
            build(EFF_RATES,       60),
            build(EFF_RATE_SECS,   60),
            build(MASTERDB_MONTHS, 30)
        ));
        return cm;
    }

    private static CaffeineCache build(String name, long minutesTtl) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .expireAfterWrite(minutesTtl, TimeUnit.MINUTES)
                .build());
    }
}
