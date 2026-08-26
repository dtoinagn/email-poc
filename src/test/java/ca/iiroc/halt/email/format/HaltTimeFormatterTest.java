package ca.iiroc.halt.email.format;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the input shape confirmed against a real sample {@code HaltMessage} payload
 * ({@code "2026-07-21T09:45:00.000"}) -- see {@link HaltTimeFormatter}'s Javadoc.
 */
class HaltTimeFormatterTest {

    @Test
    void formatsSampleHaltTimeToRequirementsDocFormat() {
        assertThat(HaltTimeFormatter.format("2026-07-21T09:45:00.000")).isEqualTo("09:45:00 AM");
    }

    @Test
    void formatsSampleResumptionTimeToRequirementsDocFormat() {
        assertThat(HaltTimeFormatter.format("2026-07-21T12:15:00.000")).isEqualTo("12:15:00 PM");
    }

    @Test
    void toleratesAMissingFractionalSecond() {
        assertThat(HaltTimeFormatter.format("2026-07-21T09:45:00")).isEqualTo("09:45:00 AM");
    }

    @Test
    void blankInputFormatsToEmptyString() {
        assertThat(HaltTimeFormatter.format(null)).isEmpty();
        assertThat(HaltTimeFormatter.format("")).isEmpty();
    }
}
