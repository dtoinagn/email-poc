package ca.iiroc.halt.email.template;

import ca.iiroc.halt.email.routing.EmailTrigger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Bilingual copy that doesn't fit a Thymeleaf template's dynamic fields — subjects, in-body headings, and
 * the handful of row labels/intro clauses still marked "French TBD" in the requirements doc. A flat
 * properties file, not Spring's Locale-based {@code MessageSource}: every CIRO halt email is one bilingual
 * document (English and French on the same line), never rendered per-viewer-locale (design doc §06).
 */
@Component
public class EmailCopy {

    private final Properties copy = new Properties();

    public EmailCopy() {
        try (InputStream in = getClass().getResourceAsStream("/email-copy.properties")) {
            if (in == null) {
                throw new IllegalStateException("email-copy.properties not found on classpath");
            }
            copy.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String subject(EmailTrigger trigger) {
        return get(trigger.key() + ".subject");
    }

    public String get(String key) {
        String value = copy.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing email-copy.properties key: " + key);
        }
        return value;
    }
}
