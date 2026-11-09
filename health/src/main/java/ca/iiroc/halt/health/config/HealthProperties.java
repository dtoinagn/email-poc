package ca.iiroc.halt.health.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Bad config fails at startup: an aggregator monitoring nothing, for example, would report UP forever.
 */
@Validated
@ConfigurationProperties("halt-health")
public record HealthProperties(
        @NotNull Duration pollInterval,
        @NotNull Duration timeout,
        @NotNull Duration staleAfter,
        @NotEmpty List<@Valid Service> services) {

    public enum ServiceType {
        ACTUATOR("https?"),
        HTTP("https?");

        private final String schemePattern;

        ServiceType(String schemePattern) {
            this.schemePattern = schemePattern;
        }
    }

    public record Service(
            @NotBlank String name,
            @DefaultValue("ACTUATOR") ServiceType type,
            @NotBlank String url,
            @DefaultValue("true") boolean critical) {

        // URI leaves the host null for a name it can't parse, such as one containing an underscore. Such
        // a url could never be checked, so it would otherwise look like a permanent outage.
        @AssertTrue(message = "url must have a valid hostname (no underscores) and match the service type: "
                + "http(s)://... for actuator and http")
        public boolean isUrlValidForType() {
            if (url == null) {
                return true;
            }
            try {
                URI uri = URI.create(url);
                return uri.getHost() != null && uri.getScheme().matches(type.schemePattern);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    @AssertTrue(message = "service names must be unique")
    public boolean isServiceNamesUnique() {
        if (services == null) {
            return true;
        }
        var names = new HashSet<String>();
        return services.stream().allMatch(service -> names.add(service.name()));
    }

    // Consecutive results can be up to poll-interval + timeout apart, so anything shorter would mark
    // healthy services as stale between checks.
    @AssertTrue(message = "stale-after must be greater than poll-interval + timeout")
    public boolean isStaleAfterLongEnough() {
        if (pollInterval == null || timeout == null || staleAfter == null) {
            return true;
        }
        return staleAfter.compareTo(pollInterval.plus(timeout)) > 0;
    }
}
