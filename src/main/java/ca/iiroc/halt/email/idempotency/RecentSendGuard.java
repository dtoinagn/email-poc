package ca.iiroc.halt.email.idempotency;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Catches a same-session Kafka redelivery before it reaches SMTP. Does <b>not</b> survive a process
 * restart — see design doc §07/§10 "no-database trade-off": correctness mainly rests on the Kafka
 * listener committing its offset only after a send succeeds or is recovered, not on this cache.
 */
@Component
public class RecentSendGuard {

    private final Cache<String, Boolean> recentlySent = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    public boolean isDuplicate(String key) {
        return recentlySent.getIfPresent(key) != null;
    }

    public void markSent(String key) {
        recentlySent.put(key, Boolean.TRUE);
    }
}
