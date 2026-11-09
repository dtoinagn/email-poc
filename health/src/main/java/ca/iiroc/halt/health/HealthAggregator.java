package ca.iiroc.halt.health;

import ca.iiroc.halt.health.AggregateHealth.OverallStatus;
import ca.iiroc.halt.health.AggregateHealth.ServiceHealth;
import ca.iiroc.halt.health.AggregateHealth.ServiceStatus;
import ca.iiroc.halt.health.config.HealthProperties;
import ca.iiroc.halt.health.probe.ProbeResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holds the latest result per service and rolls them up. Fails closed: a critical service whose state
 * is unknown takes the overall status DOWN, so neither a fresh start nor a stuck poller can report a
 * healthy status that nothing has verified.
 */
@Component
public class HealthAggregator {

    private static final Logger log = LoggerFactory.getLogger(HealthAggregator.class);

    private final HealthProperties properties;
    private final Map<String, Check> latest = new ConcurrentHashMap<>();

    public HealthAggregator(HealthProperties properties) {
        this.properties = properties;
    }

    public void record(HealthProperties.Service service, ProbeResult result, Instant checkedAt) {
        Check previous = latest.put(service.name(), new Check(result, checkedAt));
        if (previous == null || previous.result().up() != result.up()) {
            if (result.up()) {
                log.info("{} is UP", service.name());
            } else {
                log.warn("{} is DOWN: {}", service.name(), result.detail());
            }
        }
    }

    public AggregateHealth snapshot(Instant now) {
        List<ServiceHealth> services = properties.services().stream()
                .map(service -> healthOf(service, now))
                .toList();
        return new AggregateHealth(overallStatus(services), now, services);
    }

    private ServiceHealth healthOf(HealthProperties.Service service, Instant now) {
        Check check = latest.get(service.name());
        if (check == null) {
            return new ServiceHealth(service.name(), ServiceStatus.UNKNOWN, service.critical(), "not checked yet", null);
        }
        if (check.checkedAt().plus(properties.staleAfter()).isBefore(now)) {
            return new ServiceHealth(service.name(), ServiceStatus.UNKNOWN, service.critical(), "result is stale", check.checkedAt());
        }
        ServiceStatus status = check.result().up() ? ServiceStatus.UP : ServiceStatus.DOWN;
        return new ServiceHealth(service.name(), status, service.critical(), check.result().detail(), check.checkedAt());
    }

    private OverallStatus overallStatus(List<ServiceHealth> services) {
        boolean allUp = true;
        for (ServiceHealth service : services) {
            if (service.status() != ServiceStatus.UP) {
                if (service.critical()) {
                    return OverallStatus.DOWN;
                }
                allUp = false;
            }
        }
        return allUp ? OverallStatus.UP : OverallStatus.DEGRADED;
    }

    private record Check(ProbeResult result, Instant checkedAt) {
    }
}
