package ca.iiroc.halt.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.iiroc.halt.health.AggregateHealth.ServiceHealth;
import ca.iiroc.halt.health.config.HealthProperties;
import ca.iiroc.halt.health.config.HealthProperties.ServiceType;
import ca.iiroc.halt.health.probe.HealthProbe;
import ca.iiroc.halt.health.probe.ProbeResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class HealthPollerTest {

    private static final Duration TIMEOUT = Duration.ofMillis(200);

    private final HealthProperties.Service http = service("http", ServiceType.HTTP, "http://localhost");
    private final HealthProperties.Service actuator = service("actuator", ServiceType.ACTUATOR, "http://localhost");
    private final HealthProperties properties = new HealthProperties(
            Duration.ofSeconds(1), TIMEOUT, Duration.ofSeconds(5), List.of(http, actuator));
    private final HealthAggregator aggregator = new HealthAggregator(properties);

    private static HealthProperties.Service service(String name, ServiceType type, String url) {
        return new HealthProperties.Service(name, type, url, true);
    }

    private static HealthProbe probe(ServiceType type, CheckBody body) {
        return new HealthProbe() {
            @Override
            public ServiceType type() {
                return type;
            }

            @Override
            public ProbeResult check(HealthProperties.Service service) throws Exception {
                return body.run();
            }
        };
    }

    private interface CheckBody {
        ProbeResult run() throws Exception;
    }

    private ServiceHealth health(HealthProperties.Service service) {
        return aggregator.snapshot(Instant.now()).services().stream()
                .filter(health -> health.name().equals(service.name()))
                .findFirst().orElseThrow();
    }

    @Test
    void dispatchesEachServiceToTheProbeForItsType() {
        new HealthPoller(properties, List.of(
                probe(ServiceType.HTTP, ProbeResult::healthy),
                probe(ServiceType.ACTUATOR, () -> ProbeResult.unhealthy("actuator probe ran"))), aggregator).pollAll();

        assertThat(health(http).status()).isEqualTo(AggregateHealth.ServiceStatus.UP);
        assertThat(health(actuator).detail()).isEqualTo("actuator probe ran");
    }

    @Test
    void exceptionCountsAsUnhealthyAndDoesNotAffectOtherServices() {
        new HealthPoller(properties, List.of(
                probe(ServiceType.HTTP, () -> {
                    throw new IllegalStateException("secret host name");
                }),
                probe(ServiceType.ACTUATOR, ProbeResult::healthy)), aggregator).pollAll();

        assertThat(health(http).detail()).isEqualTo("IllegalStateException");
        assertThat(health(actuator).status()).isEqualTo(AggregateHealth.ServiceStatus.UP);
    }

    @Test
    void interruptsAProbeThatOverrunsTheTimeoutAndKeepsOtherResults() {
        long start = System.nanoTime();

        new HealthPoller(properties, List.of(
                probe(ServiceType.HTTP, () -> {
                    Thread.sleep(Duration.ofSeconds(30));
                    return ProbeResult.healthy();
                }),
                probe(ServiceType.ACTUATOR, ProbeResult::healthy)), aggregator).pollAll();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        assertThat(health(http).detail()).isEqualTo("timed out");
        assertThat(health(actuator).status()).isEqualTo(AggregateHealth.ServiceStatus.UP);
    }

    @Test
    void recordsTimeoutOnScheduleEvenIfProbeIgnoresTheInterrupt() {
        AtomicBoolean release = new AtomicBoolean();
        long start = System.nanoTime();

        new HealthPoller(properties, List.of(
                probe(ServiceType.HTTP, () -> {
                    while (!release.get()) {
                        Thread.onSpinWait();
                        Thread.yield();
                    }
                    return ProbeResult.healthy();
                }),
                probe(ServiceType.ACTUATOR, ProbeResult::healthy)), aggregator).pollAll();

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        release.set(true);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        assertThat(health(http).detail()).isEqualTo("timed out");
    }

    @Test
    void refusesToStartWhenAConfiguredTypeHasNoProbe() {
        assertThatThrownBy(() -> new HealthPoller(properties, List.of(probe(ServiceType.HTTP, ProbeResult::healthy)), aggregator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("actuator");
    }
}
