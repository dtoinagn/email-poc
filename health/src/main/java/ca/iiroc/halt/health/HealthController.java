package ca.iiroc.halt.health;

import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The load balancer contract: 200 means route traffic here, 503 means don't. Answers HEAD as well as GET.
 */
@RestController
public class HealthController {

    private final HealthAggregator aggregator;

    public HealthController(HealthAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @GetMapping("/health")
    public ResponseEntity<AggregateHealth> health() {
        AggregateHealth health = aggregator.snapshot(Instant.now());
        HttpStatus status = health.status() == AggregateHealth.OverallStatus.DOWN
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(health);
    }
}
