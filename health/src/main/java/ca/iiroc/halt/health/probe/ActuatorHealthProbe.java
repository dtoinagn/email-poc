package ca.iiroc.halt.health.probe;

import ca.iiroc.halt.health.config.HealthProperties;
import ca.iiroc.halt.health.config.HealthProperties.ServiceType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Healthy only on a 2xx response whose body says {@code "status": "UP"}. Everything else, including
 * redirects, non-JSON responses and custom Actuator statuses, is unhealthy.
 */
@Component
public class ActuatorHealthProbe implements HealthProbe {

    private static final String STATUS_UP = "UP";

    private final RestClient restClient;

    public ActuatorHealthProbe(RestClient probeRestClient) {
        this.restClient = probeRestClient;
    }

    @Override
    public ServiceType type() {
        return ServiceType.ACTUATOR;
    }

    @Override
    public ProbeResult check(HealthProperties.Service service) {
        try {
            ActuatorHealth health = restClient.get().uri(URI.create(service.url())).retrieve().body(ActuatorHealth.class);
            if (health == null || health.status() == null) {
                return ProbeResult.unhealthy("no status in response");
            }
            return STATUS_UP.equals(health.status())
                    ? ProbeResult.healthy()
                    : ProbeResult.unhealthy("status " + health.status());
        } catch (RestClientException e) {
            return HttpProbeSupport.failure(e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ActuatorHealth(String status) {
    }
}
