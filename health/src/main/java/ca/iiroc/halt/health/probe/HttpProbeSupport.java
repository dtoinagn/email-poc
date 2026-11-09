package ca.iiroc.halt.health.probe;

import ca.iiroc.halt.health.config.HealthProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * What the HTTP-based probes share: one client, which doesn't follow redirects, and failure reporting.
 */
@Configuration
public class HttpProbeSupport {

    @Bean
    public RestClient probeRestClient(RestClient.Builder builder, HealthProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return builder.requestFactory(requestFactory).build();
    }

    static ProbeResult failure(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            return ProbeResult.unhealthy("HTTP " + response.getStatusCode().value());
        }
        if (e instanceof ResourceAccessException) {
            // The direct cause says what happened (ConnectException, HttpTimeoutException); the root
            // cause is a JDK internal such as ClosedChannelException.
            return ProbeResult.unhealthy(e.getCause().getClass().getSimpleName());
        }
        return ProbeResult.unhealthy("invalid response");
    }
}
