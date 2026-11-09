package ca.iiroc.halt.health;

import ca.iiroc.halt.health.config.HealthProperties;
import ca.iiroc.halt.health.config.HealthProperties.ServiceType;
import ca.iiroc.halt.health.probe.HealthProbe;
import ca.iiroc.halt.health.probe.ProbeResult;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checks every service in the background so that load balancer requests only ever read cached results:
 * probe traffic to the aggregator never turns into traffic to the monitored services.
 */
@Component
public class HealthPoller {

    private final HealthProperties properties;
    private final Map<ServiceType, HealthProbe> probes = new EnumMap<>(ServiceType.class);
    private final HealthAggregator aggregator;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public HealthPoller(HealthProperties properties, List<HealthProbe> probes, HealthAggregator aggregator) {
        this.properties = properties;
        this.aggregator = aggregator;
        probes.forEach(probe -> this.probes.put(probe.type(), probe));
        properties.services().forEach(service -> {
            if (!this.probes.containsKey(service.type())) {
                throw new IllegalStateException("No probe for " + service.name() + " of type " + service.type());
            }
        });
    }

    @Scheduled(fixedDelayString = "${halt-health.poll-interval}")
    public void pollAll() {
        long deadline = System.nanoTime() + properties.timeout().toNanos();

        // One virtual thread per service, so the whole round takes at most one timeout however many are slow.
        Map<HealthProperties.Service, Future<ProbeResult>> checks = new LinkedHashMap<>();
        properties.services().forEach(service ->
                checks.put(service, executor.submit(() -> probes.get(service.type()).check(service))));

        // Results are recorded here rather than on the check's own thread, so a check that overruns gets
        // a result on time even if it ignores the interrupt.
        checks.forEach((service, check) -> aggregator.record(service, await(check, deadline), Instant.now()));
    }

    private ProbeResult await(Future<ProbeResult> check, long deadline) {
        try {
            return check.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            check.cancel(true);
            return ProbeResult.unhealthy("timed out");
        } catch (ExecutionException e) {
            return ProbeResult.unhealthy(e.getCause().getClass().getSimpleName());
        } catch (InterruptedException e) {
            check.cancel(true);
            Thread.currentThread().interrupt();
            return ProbeResult.unhealthy("interrupted");
        }
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }
}
