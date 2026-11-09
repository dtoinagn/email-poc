package ca.iiroc.halt.health.probe;

import ca.iiroc.halt.health.config.HealthProperties;
import ca.iiroc.halt.health.config.HealthProperties.ServiceType;

/**
 * How one type of service is checked. To monitor something new, add a {@link ServiceType} and a bean
 * implementing this interface.
 */
public interface HealthProbe {

    ServiceType type();

    /**
     * Called concurrently, on a thread that is interrupted once the configured timeout has passed.
     *
     * @throws Exception treated as an unhealthy result, with the exception's class name as the detail
     */
    ProbeResult check(HealthProperties.Service service) throws Exception;
}
