package ca.iiroc.halt.health;

import static org.assertj.core.api.Assertions.assertThat;

import ca.iiroc.halt.health.config.HealthProperties;
import ca.iiroc.halt.health.config.HealthProperties.ServiceType;
import ca.iiroc.halt.health.probe.ActuatorHealthProbe;
import ca.iiroc.halt.health.probe.HttpProbeSupport;
import ca.iiroc.halt.health.probe.ProbeResult;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ActuatorHealthProbeTest {

    private static final Duration TIMEOUT = Duration.ofMillis(300);

    private final StubActuator stub = new StubActuator();
    private final ActuatorHealthProbe probe = new ActuatorHealthProbe(new HttpProbeSupport().probeRestClient(
            RestClient.builder(),
            new HealthProperties(Duration.ofSeconds(1), TIMEOUT, Duration.ofSeconds(5), List.of())));

    @AfterEach
    void stopStub() {
        stub.close();
    }

    private ProbeResult check() {
        return check(stub.url());
    }

    private ProbeResult check(URI url) {
        return probe.check(new HealthProperties.Service("svc", ServiceType.ACTUATOR, url.toString(), true));
    }

    @Test
    void upWhenStatusIsUp() {
        assertThat(check()).isEqualTo(ProbeResult.healthy());
    }

    @Test
    void ignoresComponentDetailsInTheBody() {
        stub.respond(200, "{\"status\":\"UP\",\"components\":{\"db\":{\"status\":\"UP\"}}}");

        assertThat(check().up()).isTrue();
    }

    @Test
    void downOnActuator503() {
        stub.respond(503, "{\"status\":\"DOWN\"}");

        assertThat(check()).isEqualTo(ProbeResult.unhealthy("HTTP 503"));
    }

    @Test
    void downOnNonUpStatusReturnedWith200() {
        stub.respond(200, "{\"status\":\"MAINTENANCE\"}");

        assertThat(check()).isEqualTo(ProbeResult.unhealthy("status MAINTENANCE"));
    }

    @Test
    void downWhenBodyHasNoStatus() {
        stub.respond(200, "{}");

        assertThat(check()).isEqualTo(ProbeResult.unhealthy("no status in response"));
    }

    @Test
    void downOnNonJsonResponse() {
        stub.contentType("text/html").respond(200, "<html>login</html>");

        assertThat(check()).isEqualTo(ProbeResult.unhealthy("invalid response"));
    }

    @Test
    void downOnMalformedJson() {
        stub.respond(200, "{not json");

        assertThat(check()).isEqualTo(ProbeResult.unhealthy("invalid response"));
    }

    @Test
    void downWhenNothingIsListening() {
        URI url = stub.url();
        stub.close();

        assertThat(check(url)).isEqualTo(ProbeResult.unhealthy("ConnectException"));
    }

    @Test
    void downWithinTheTimeoutWhenServiceHangs() {
        stub.delay(Duration.ofSeconds(5));

        long start = System.nanoTime();
        ProbeResult result = check();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result).isEqualTo(ProbeResult.unhealthy("HttpTimeoutException"));
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void failureDetailDoesNotLeakTheUrl() {
        URI url = stub.url();
        stub.close();

        assertThat(check(url).detail()).doesNotContain("localhost");
    }
}
