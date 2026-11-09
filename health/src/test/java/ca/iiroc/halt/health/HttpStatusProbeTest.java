package ca.iiroc.halt.health;

import static org.assertj.core.api.Assertions.assertThat;

import ca.iiroc.halt.health.config.HealthProperties;
import ca.iiroc.halt.health.config.HealthProperties.ServiceType;
import ca.iiroc.halt.health.probe.HttpProbeSupport;
import ca.iiroc.halt.health.probe.HttpStatusProbe;
import ca.iiroc.halt.health.probe.ProbeResult;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpStatusProbeTest {

    private final StubActuator stub = new StubActuator();
    private final HttpStatusProbe probe = new HttpStatusProbe(new HttpProbeSupport().probeRestClient(
            RestClient.builder(),
            new HealthProperties(Duration.ofSeconds(1), Duration.ofMillis(300), Duration.ofSeconds(5), List.of())));

    @AfterEach
    void stopStub() {
        stub.close();
    }

    private ProbeResult check(URI url) {
        return probe.check(new HealthProperties.Service("site", ServiceType.HTTP, url.toString(), true));
    }

    @Test
    void upOn200WhateverTheBody() {
        stub.contentType("text/html").respond(200, "<html>login</html>");

        assertThat(check(stub.url())).isEqualTo(ProbeResult.healthy());
    }

    @Test
    void downOnOther2xx() {
        stub.respond(202, "accepted");

        assertThat(check(stub.url())).isEqualTo(ProbeResult.unhealthy("HTTP 202"));
    }

    @Test
    void downOnRedirectBecauseItIsNotFollowed() {
        stub.respond(302, "moved");

        assertThat(check(stub.url())).isEqualTo(ProbeResult.unhealthy("HTTP 302"));
    }

    @Test
    void downOnServerError() {
        stub.respond(500, "oops");

        assertThat(check(stub.url())).isEqualTo(ProbeResult.unhealthy("HTTP 500"));
    }

    @Test
    void downWhenNothingIsListening() {
        URI url = stub.url();
        stub.close();

        assertThat(check(url)).isEqualTo(ProbeResult.unhealthy("ConnectException"));
    }
}
