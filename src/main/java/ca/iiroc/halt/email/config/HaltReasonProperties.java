package ca.iiroc.halt.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * {@code HaltMessage} carries only one halt-reason description (assumed English) — there is no French
 * field on the wire model at all (see design doc §04). This is the local {@code haltReasonCode -> French}
 * lookup {@link ca.iiroc.halt.email.format.ReasonTranslator} falls back to English against, seeded empty
 * until Communications/Surveillance supplies real entries.
 */
@ConfigurationProperties(prefix = "email-service.halt-reasons")
public record HaltReasonProperties(Map<String, String> french) {

    public HaltReasonProperties {
        if (french == null) {
            french = Map.of();
        }
    }
}
