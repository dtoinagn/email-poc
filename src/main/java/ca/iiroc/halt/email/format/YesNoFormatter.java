package ca.iiroc.halt.email.format;

/**
 * {@code RegHaltMessageMapperImpl} maps the wire model's {@code bool allIssue} to
 * {@code HaltMessage.allIssue} via {@code String.valueOf(boolean)} — i.e. the literal string
 * {@code "true"}/{@code "false"}, not the "Yes/Oui" the requirements doc's email body needs. That mapping
 * is this service's job (see design doc §04).
 */
public final class YesNoFormatter {

    private YesNoFormatter() {
    }

    public static String bilingual(String allIssueRaw) {
        return Boolean.parseBoolean(allIssueRaw) ? "Yes/Oui" : "No/Non";
    }
}
