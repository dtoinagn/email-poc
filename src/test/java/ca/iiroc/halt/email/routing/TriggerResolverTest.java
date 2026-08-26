package ca.iiroc.halt.email.routing;

import ca.ciro.halt.model.HaltMessage;
import ca.ciro.halt.state.Command;
import ca.ciro.halt.state.SubState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer 1 of the testing strategy (design doc §11): one assertion per row of the §03 "Event routing"
 * table, plus the ambiguous cases -- no Kafka, no Spring context, no infra at all.
 */
class TriggerResolverTest {

    private final TriggerResolver resolver = new TriggerResolver();

    @Test
    void immediateHaltSentTriggersTraditionalHalt() {
        assertThat(resolve(Command.SUBMIT_IMMEDIATE_HALT, SubState.Halt_Sent, null, null))
                .contains(EmailTrigger.TRADITIONAL_HALT);
    }

    @Test
    void scheduledHaltTriggeredSentTriggersTraditionalHalt() {
        assertThat(resolve(Command.TRIGGER_SCHEDULED_HALT, SubState.Halt_Sent, null, null))
                .contains(EmailTrigger.TRADITIONAL_HALT);
    }

    @Test
    void sscbHaltSentTriggersSscbHalt() {
        assertThat(resolve(Command.SUBMIT_SSCB_HALT, SubState.Halt_Sent, null, null))
                .contains(EmailTrigger.SSCB_HALT);
    }

    @Test
    void sscbExtendHaltUpdateSentTriggersSscbExtension() {
        assertThat(resolve(Command.EXTEND_SSCB_HALT, SubState.Halt_Update_Sent, null, null))
                .contains(EmailTrigger.SSCB_EXTENSION);
    }

    @Test
    void haltExtendedIsIgnoredEvenForExtendCommand() {
        // Confirmed: Halt_Extended never represents a completed FIX send -- always a no-op.
        assertThat(resolve(Command.EXTEND_SSCB_HALT, SubState.Halt_Extended, null, null)).isEmpty();
    }

    @Test
    void convertedToRegTriggersSscbToTraditional() {
        assertThat(resolve(Command.CONVERT_TO_REG_HALT, SubState.SSCB_to_Reg_Converted, null, null))
                .contains(EmailTrigger.SSCB_TO_TRADITIONAL);
    }

    @Test
    void reasonUpdateSentTriggersHaltReasonUpdate() {
        assertThat(resolve(Command.UPDATE_HALT_REASON, SubState.Halt_Update_Sent, null, null))
                .contains(EmailTrigger.HALT_REASON_UPDATE);
    }

    @Test
    void resumptionCancellationSentTriggersResumptionCancellation() {
        assertThat(resolve(Command.CANCEL_RESUMPTION, SubState.Resumption_Cancellation_Sent, null, null))
                .contains(EmailTrigger.RESUMPTION_CANCELLATION);
    }

    @Test
    void firstResumptionSentTriggersResumption() {
        assertThat(resolve(Command.SUBMIT_RESUMPTION, SubState.Resumption_Sent,
                "2026-08-25T09:00:00", "2026-08-25T09:00:00"))
                .contains(EmailTrigger.RESUMPTION);
    }

    @Test
    void resumptionSentWithBlankLastModifiedTriggersResumption() {
        assertThat(resolve(Command.SUBMIT_RESUMPTION, SubState.Resumption_Sent, "2026-08-25T09:00:00", null))
                .contains(EmailTrigger.RESUMPTION);
    }

    @Test
    void updatedResumptionSentTriggersResumptionTimeUpdate() {
        assertThat(resolve(Command.SUBMIT_RESUMPTION, SubState.Resumption_Sent,
                "2026-08-25T09:00:00", "2026-08-25T09:15:00"))
                .contains(EmailTrigger.RESUMPTION_TIME_UPDATE);
    }

    @Test
    void draftHaltIsObservedNotEmailed() {
        assertThat(resolve(Command.DRAFT_HALT, SubState.Halt_Drafted, null, null)).isEmpty();
    }

    @Test
    void cancelScheduledHaltIsObservedNotEmailed() {
        assertThat(resolve(Command.CANCEL_SCHEDULED_HALT, SubState.Pending_Halt_Cancelled, null, null)).isEmpty();
    }

    @Test
    void updateHaltDetailsIsObservedNotEmailed() {
        assertThat(resolve(Command.UPDATE_HALT_DETAILS, SubState.Halt_Updated, null, null)).isEmpty();
    }

    @Test
    void wrongSubStateForCommandDoesNotTrigger() {
        // Halt gateway confirms the FIX send at Halt_Sent -- anything earlier in the flow is a no-op.
        assertThat(resolve(Command.SUBMIT_IMMEDIATE_HALT, SubState.Halt_Initiated, null, null)).isEmpty();
    }

    private Optional<EmailTrigger> resolve(Command command, SubState subState, String createdTime, String lastModifiedTime) {
        HaltMessage message = HaltMessage.builder()
                .haltId("H-TEST")
                .command(command)
                .subState(subState)
                .createdTime(createdTime)
                .lastModifiedTime(lastModifiedTime)
                .action("ModifyScheduledResumption")
                .build();
        return resolver.resolve(message);
    }
}
