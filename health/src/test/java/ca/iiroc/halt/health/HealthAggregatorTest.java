package ca.iiroc.halt.health;

import static org.assertj.core.api.Assertions.assertThat;

import ca.iiroc.halt.health.AggregateHealth.OverallStatus;
import ca.iiroc.halt.health.AggregateHealth.ServiceStatus;
import ca.iiroc.halt.health.config.HealthProperties;
import ca.iiroc.halt.health.config.HealthProperties.ServiceType;
import ca.iiroc.halt.health.probe.ProbeResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthAggregatorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration STALE_AFTER = Duration.ofSeconds(15);

    private final HealthProperties.Service critical = service("critical", true);
    private final HealthProperties.Service optional = service("optional", false);
    private final HealthAggregator aggregator = new HealthAggregator(new HealthProperties(
            Duration.ofSeconds(5), Duration.ofSeconds(2), STALE_AFTER, List.of(critical, optional)));

    private static HealthProperties.Service service(String name, boolean critical) {
        return new HealthProperties.Service(name, ServiceType.ACTUATOR, "http://localhost/actuator/health", critical);
    }

    @Test
    void upWhenEverythingIsUp() {
        aggregator.record(critical, ProbeResult.healthy(), NOW);
        aggregator.record(optional, ProbeResult.healthy(), NOW);

        assertThat(aggregator.snapshot(NOW).status()).isEqualTo(OverallStatus.UP);
    }

    @Test
    void downWhenCriticalServiceIsDown() {
        aggregator.record(critical, ProbeResult.unhealthy("HTTP 503"), NOW);
        aggregator.record(optional, ProbeResult.healthy(), NOW);

        AggregateHealth health = aggregator.snapshot(NOW);

        assertThat(health.status()).isEqualTo(OverallStatus.DOWN);
        assertThat(health.services().get(0).status()).isEqualTo(ServiceStatus.DOWN);
        assertThat(health.services().get(0).detail()).isEqualTo("HTTP 503");
    }

    @Test
    void degradedWhenOnlyNonCriticalServiceIsDown() {
        aggregator.record(critical, ProbeResult.healthy(), NOW);
        aggregator.record(optional, ProbeResult.unhealthy("HTTP 503"), NOW);

        assertThat(aggregator.snapshot(NOW).status()).isEqualTo(OverallStatus.DEGRADED);
    }

    @Test
    void downBeforeAnyCheckHasCompleted() {
        AggregateHealth health = aggregator.snapshot(NOW);

        assertThat(health.status()).isEqualTo(OverallStatus.DOWN);
        assertThat(health.services()).allSatisfy(service -> assertThat(service.status()).isEqualTo(ServiceStatus.UNKNOWN));
    }

    @Test
    void criticalResultOlderThanStaleAfterTakesOverallDown() {
        aggregator.record(critical, ProbeResult.healthy(), NOW);
        aggregator.record(optional, ProbeResult.healthy(), NOW);

        AggregateHealth health = aggregator.snapshot(NOW.plus(STALE_AFTER).plusMillis(1));

        assertThat(health.status()).isEqualTo(OverallStatus.DOWN);
        assertThat(health.services().get(0).status()).isEqualTo(ServiceStatus.UNKNOWN);
        assertThat(health.services().get(0).checkedAt()).isEqualTo(NOW);
    }

    @Test
    void resultExactlyAtStaleAfterIsStillFresh() {
        aggregator.record(critical, ProbeResult.healthy(), NOW);
        aggregator.record(optional, ProbeResult.healthy(), NOW);

        assertThat(aggregator.snapshot(NOW.plus(STALE_AFTER)).status()).isEqualTo(OverallStatus.UP);
    }

    @Test
    void servicesAreReportedInConfiguredOrder() {
        aggregator.record(optional, ProbeResult.healthy(), NOW);
        aggregator.record(critical, ProbeResult.healthy(), NOW);

        assertThat(aggregator.snapshot(NOW).services())
                .extracting(AggregateHealth.ServiceHealth::name)
                .containsExactly("critical", "optional");
    }
}
