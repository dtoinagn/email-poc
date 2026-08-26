package ca.iiroc.halt.email.routing;

import ca.ciro.halt.model.HaltMessage;
import ca.ciro.halt.state.Command;
import ca.ciro.halt.state.SubState;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Maps a {@code HaltMessage}'s {@code (command, subState)} — plus, for resumptions only, a
 * createdTime/lastModifiedTime tiebreaker — to the email it should trigger. See the design doc §03
 * "Event routing" for the full rationale, including the tiebreaker flagged as an assumption to confirm
 * against the halt-gateway-core producer.
 *
 * <p>Anything not listed below — {@code DRAFT_HALT}, {@code DRAFT_RESUMPTION},
 * {@code CANCEL_SCHEDULED_HALT}, {@code TRIGGER_RESUMPTION}, {@code UPDATE_HALT_DETAILS}, every
 * {@code *_Drafted}/{@code Pending_Halt_*} subState, and any future {@code (command, subState)} combo
 * this resolver has never seen — is observed, not emailed: an empty {@link Optional}, not an exception.
 * {@code Halt_Extended} is explicitly in that ignored set: confirmed as an internal/transitional
 * subState that never represents a completed FIX send, not a gate for any email.
 */
@Component
public class TriggerResolver {

    public Optional<EmailTrigger> resolve(HaltMessage message) {
        Command command = message.getCommand();
        SubState subState = message.getSubState();
        if (command == null || subState == null) {
            return Optional.empty();
        }

        return switch (command) {
            case SUBMIT_IMMEDIATE_HALT, TRIGGER_SCHEDULED_HALT ->
                    onlyIf(subState == SubState.Halt_Sent, EmailTrigger.TRADITIONAL_HALT);

            case SUBMIT_SSCB_HALT ->
                    onlyIf(subState == SubState.Halt_Sent, EmailTrigger.SSCB_HALT);

            // Halt_Extended is confirmed ignored -- it never marks a completed FIX send. Extension shares
            // its post-FIX-send subState with UPDATE_HALT_REASON; command still disambiguates the email.
            case EXTEND_SSCB_HALT ->
                    onlyIf(subState == SubState.Halt_Update_Sent, EmailTrigger.SSCB_EXTENSION);

            case CONVERT_TO_REG_HALT ->
                    onlyIf(subState == SubState.SSCB_to_Reg_Converted, EmailTrigger.SSCB_TO_TRADITIONAL);

            case UPDATE_HALT_REASON ->
                    onlyIf(subState == SubState.Halt_Update_Sent, EmailTrigger.HALT_REASON_UPDATE);

            case CANCEL_RESUMPTION ->
                    onlyIf(subState == SubState.Resumption_Cancellation_Sent, EmailTrigger.RESUMPTION_CANCELLATION);

            case SUBMIT_RESUMPTION -> subState == SubState.Resumption_Sent
                    ? Optional.of(isFirstResumption(message) ? EmailTrigger.RESUMPTION : EmailTrigger.RESUMPTION_TIME_UPDATE)
                    : Optional.empty();

            default -> Optional.empty();
        };
    }

    /**
     * {@code command}+{@code subState} alone can't distinguish "create resumption" from "update
     * resumption" — both are {@code SUBMIT_RESUMPTION} / {@code Resumption_Sent} (the enum's own comment
     * reads "create or update resumption"). Tiebreaker: an untouched {@code lastModifiedTime} means first
     * send. Flagged in the design doc §03/§10 as an assumption to confirm against the producer.
     */
    private boolean isFirstResumption(HaltMessage message) {
        String lastModifiedTime = message.getLastModifiedTime();
        return lastModifiedTime == null
                || lastModifiedTime.isBlank()
                || lastModifiedTime.equals(message.getCreatedTime());
    }

    private Optional<EmailTrigger> onlyIf(boolean gateReached, EmailTrigger trigger) {
        return gateReached ? Optional.of(trigger) : Optional.empty();
    }
}
