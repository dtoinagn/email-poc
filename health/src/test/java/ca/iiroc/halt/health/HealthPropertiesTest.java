package ca.iiroc.halt.health;

import static org.assertj.core.api.Assertions.assertThat;

import ca.iiroc.halt.health.config.HealthProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class HealthPropertiesTest {

    private static final String[] TIMINGS = {
            "halt-health.poll-interval=5s",
            "halt-health.timeout=2s",
            "halt-health.stale-after=15s"
    };
    private static final String URL = "http://localhost:8081/actuator/health";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Configuration
    @EnableConfigurationProperties(HealthProperties.class)
    static class Config {
    }

    @Test
    void bindsServicesWithCriticalDefaultingToTrue() {
        runner.withPropertyValues(TIMINGS)
                .withPropertyValues(
                        "halt-health.services[0].name=a",
                        "halt-health.services[0].url=" + URL,
                        "halt-health.services[1].name=b",
                        "halt-health.services[1].url=" + URL,
                        "halt-health.services[1].critical=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    HealthProperties properties = context.getBean(HealthProperties.class);
                    assertThat(properties.services().get(0).critical()).isTrue();
                    assertThat(properties.services().get(1).critical()).isFalse();
                });
    }

    @Test
    void bindsTheType() {
        runner.withPropertyValues(TIMINGS)
                .withPropertyValues(
                        "halt-health.services[0].name=web",
                        "halt-health.services[0].type=http",
                        "halt-health.services[0].url=https://web.internal/")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    HealthProperties.Service service = context.getBean(HealthProperties.class).services().get(0);
                    assertThat(service.type()).isEqualTo(HealthProperties.ServiceType.HTTP);
                });
    }

    @Test
    void acceptsAnIpAddressAsTheHost() {
        runner.withPropertyValues(TIMINGS)
                .withPropertyValues(
                        "halt-health.services[0].name=web",
                        "halt-health.services[0].type=http",
                        "halt-health.services[0].url=https://10.20.30.40/login")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void typeDefaultsToActuator() {
        runner.withPropertyValues(TIMINGS)
                .withPropertyValues("halt-health.services[0].name=a", "halt-health.services[0].url=" + URL)
                .run(context -> {
                    HealthProperties.Service service = context.getBean(HealthProperties.class).services().get(0);
                    assertThat(service.type()).isEqualTo(HealthProperties.ServiceType.ACTUATOR);
                });
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "actuator | localhost:8081",
            "http     | ftp://localhost",
            "http     | http://host_ip.sample.ca/",
    })
    void rejectsUrlThatDoesNotMatchTheType(String type, String url) {
        runner.withPropertyValues(TIMINGS)
                .withPropertyValues(
                        "halt-health.services[0].name=a",
                        "halt-health.services[0].type=" + type,
                        "halt-health.services[0].url=" + url)
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().hasMessageContaining("url must have a valid hostname"));
    }

    @Test
    void rejectsEmptyServiceList() {
        runner.withPropertyValues(TIMINGS)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsDuplicateServiceNames() {
        runner.withPropertyValues(TIMINGS)
                .withPropertyValues(
                        "halt-health.services[0].name=a",
                        "halt-health.services[0].url=" + URL,
                        "halt-health.services[1].name=a",
                        "halt-health.services[1].url=" + URL)
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().hasMessageContaining("service names must be unique"));
    }

    @Test
    void rejectsServiceWithoutUrl() {
        runner.withPropertyValues(TIMINGS)
                .withPropertyValues("halt-health.services[0].name=a")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsStaleAfterThatWouldFlagHealthyServices() {
        runner.withPropertyValues(
                        "halt-health.poll-interval=5s",
                        "halt-health.timeout=2s",
                        "halt-health.stale-after=7s",
                        "halt-health.services[0].name=a",
                        "halt-health.services[0].url=" + URL)
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().hasMessageContaining("stale-after must be greater"));
    }
}
