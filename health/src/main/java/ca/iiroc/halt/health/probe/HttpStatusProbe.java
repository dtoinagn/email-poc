package ca.iiroc.halt.health.probe;

import ca.iiroc.halt.health.config.HealthProperties;
import ca.iiroc.halt.health.config.HealthProperties.ServiceType;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Healthy only on exactly HTTP 200. A redirect is not followed and counts as unhealthy, so the url must
 * be the final address of the page.
 */
@Component
public class HttpStatusProbe implements HealthProbe {

    private final RestClient restClient;

    public HttpStatusProbe(RestClient probeRestClient) {
        this.restClient = probeRestClient;
    }

    @Override
    public ServiceType type() {
        return ServiceType.HTTP;
    }

    @Override
    public ProbeResult check(HealthProperties.Service service) {
        try {
            int status = restClient.get().uri(URI.create(service.url())).retrieve().toBodilessEntity()
                    .getStatusCode().value();
            return status == 200 ? ProbeResult.healthy() : ProbeResult.unhealthy("HTTP " + status);
        } catch (RestClientException e) {
            return HttpProbeSupport.failure(e);
        }
    }
}
