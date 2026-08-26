package ca.iiroc.halt.email.format;

import ca.ciro.halt.model.HaltMessage;
import ca.iiroc.halt.email.config.HaltReasonProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code HaltMessage} carries only {@code haltReasonDescription} (assumed English), {@code haltReasonCode}
 * and {@code haltReasonType} — no French text at all. French is looked up locally by
 * {@code haltReasonCode}; an unmapped code falls back to the English description rather than shipping a
 * blank French value, per the i18n-ready decision (design doc §04, §10).
 */
@Component
@RequiredArgsConstructor
public class ReasonTranslator {

    private final HaltReasonProperties properties;

    public String english(HaltMessage message) {
        return message.getHaltReasonDescription();
    }

    public String french(HaltMessage message) {
        String french = properties.french().get(message.getHaltReasonCode());
        return (french != null && !french.isBlank()) ? french : english(message);
    }
}
