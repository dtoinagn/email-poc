package ca.iiroc.halt.email.routing;

/**
 * The eight emails from the requirements doc. {@link #key()} is shared by the Thymeleaf template name
 * (under {@code templates/}) and the {@code email-copy.properties} key prefix, so adding a trigger means
 * adding one template file and one copy entry with the matching name — nothing else to wire up.
 */
public enum EmailTrigger {

    TRADITIONAL_HALT("traditional-halt"),
    SSCB_HALT("sscb-halt"),
    SSCB_EXTENSION("sscb-extension"),
    SSCB_TO_TRADITIONAL("sscb-to-reg"),
    HALT_REASON_UPDATE("reason-update"),
    RESUMPTION_CANCELLATION("resumption-cancellation"),
    RESUMPTION("resumption"),
    RESUMPTION_TIME_UPDATE("resumption-time-update");

    private final String key;

    EmailTrigger(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
