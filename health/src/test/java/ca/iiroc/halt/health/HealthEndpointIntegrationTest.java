package ca.iiroc.halt.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIntegrationTest {

    private static final StubActuator CRITICAL = new StubActuator();
    private static final StubActuator OPTIONAL = new StubActuator();
    private static final String UP = "{\"status\":\"UP\"}";
    private static final String DOWN = "{\"status\":\"DOWN\"}";

    @Autowired
    private TestRestTemplate rest;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("halt-health.poll-interval", () -> "100ms");
        registry.add("halt-health.timeout", () -> "500ms");
        registry.add("halt-health.stale-after", () -> "5s");
        registry.add("halt-health.services[0].name", () -> "critical");
        registry.add("halt-health.services[0].url", CRITICAL::url);
        registry.add("halt-health.services[1].name", () -> "optional");
        registry.add("halt-health.services[1].url", OPTIONAL::url);
        registry.add("halt-health.services[1].critical", () -> "false");
    }

    @BeforeEach
    void allHealthy() {
        CRITICAL.respond(200, UP);
        OPTIONAL.respond(200, UP);
        awaitOverall(HttpStatus.OK, "UP");
    }

    @AfterAll
    static void stopStubs() {
        CRITICAL.close();
        OPTIONAL.close();
    }

    private void awaitOverall(HttpStatus httpStatus, String overall) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<String> response = rest.getForEntity("/health", String.class);
            assertThat(response.getStatusCode()).isEqualTo(httpStatus);
            assertThat(response.getBody()).startsWith("{\"status\":\"" + overall + "\"");
        });
    }

    @Test
    void reports200WhenAllServicesAreUp() {
        ResponseEntity<String> response = rest.getForEntity("/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).contains("\"name\":\"critical\"", "\"name\":\"optional\"");
    }

    @Test
    void reports503WhenCriticalServiceGoesDownAndRecovers() {
        CRITICAL.respond(503, DOWN);
        awaitOverall(HttpStatus.SERVICE_UNAVAILABLE, "DOWN");

        ResponseEntity<String> response = rest.getForEntity("/health", String.class);
        assertThat(response.getBody()).contains("HTTP 503");

        CRITICAL.respond(200, UP);
        awaitOverall(HttpStatus.OK, "UP");
    }

    @Test
    void stays200WhenOnlyNonCriticalServiceIsDown() {
        OPTIONAL.respond(503, DOWN);

        awaitOverall(HttpStatus.OK, "DEGRADED");
    }

    @Test
    void answersHeadRequests() {
        ResponseEntity<Void> up = rest.exchange("/health", HttpMethod.HEAD, HttpEntity.EMPTY, Void.class);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);

        CRITICAL.respond(503, DOWN);
        awaitOverall(HttpStatus.SERVICE_UNAVAILABLE, "DOWN");

        ResponseEntity<Void> down = rest.exchange("/health", HttpMethod.HEAD, HttpEntity.EMPTY, Void.class);
        assertThat(down.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void ownActuatorHealthIsIndependentOfMonitoredServices() {
        CRITICAL.respond(503, DOWN);
        awaitOverall(HttpStatus.SERVICE_UNAVAILABLE, "DOWN");

        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
