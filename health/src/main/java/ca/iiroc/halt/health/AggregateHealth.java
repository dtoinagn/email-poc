package ca.iiroc.halt.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record AggregateHealth(OverallStatus status, Instant evaluatedAt, List<ServiceHealth> services) {

    public enum OverallStatus {
        /** Every service is up. */
        UP,
        /** Every critical service is up, but a non-critical one is not. Still passes the probe. */
        DEGRADED,
        /** A critical service is not up. Fails the probe. */
        DOWN
    }

    public enum ServiceStatus {
        UP,
        DOWN,
        /** No check has completed yet, or the latest result is stale. */
        UNKNOWN
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServiceHealth(
            String name,
            ServiceStatus status,
            boolean critical,
            String detail,
            Instant checkedAt) {
    }
}
