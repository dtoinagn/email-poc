package ca.iiroc.halt.email.format;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * {@code haltTime}/{@code resumptionTime} arrive on {@code HaltMessage} as raw, unformatted strings —
 * {@code RegHaltMessageMapperImpl} passes them straight through. The input pattern below
 * ({@code yyyy-MM-dd'T'HH:mm:ss[.SSS]}) is grounded in an actual sample {@code HaltMessage} payload
 * (e.g. {@code "2026-07-21T09:45:00.000"}) rather than guessed — {@code ca.ciro.halt.util.Util}'s own
 * parser was tried first and does <b>not</b> accept this shape, so this service owns the parsing outright
 * rather than routing through it. Only the requirements doc's output format (HH:MM:SS AM/PM) was ever
 * meant to be this service's responsibility (see design doc §04); the milliseconds are made optional here
 * as a small hedge against a payload that omits a zero fraction.
 */
public final class HaltTimeFormatter {

    private static final DateTimeFormatter INPUT_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
            .toFormatter(Locale.ENGLISH);

    private static final DateTimeFormatter TIME_OUTPUT = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_OUTPUT = DateTimeFormatter.ISO_LOCAL_DATE;

    private HaltTimeFormatter() {
    }

    public static String format(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) {
            return "";
        }
        LocalDateTime parsed = LocalDateTime.parse(rawTime, INPUT_FORMAT);
        return parsed.toLocalTime().format(TIME_OUTPUT);
    }

    public static String currentDate() {
        return LocalDate.now().format(DATE_OUTPUT);
    }
}
