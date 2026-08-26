package ca.iiroc.halt.email;

import ca.ciro.halt.model.HaltMessage;
import ca.ciro.halt.state.Command;
import ca.ciro.halt.state.State;
import ca.ciro.halt.state.SubState;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer 2 of the testing strategy (design doc §11): {@link HaltEventProcessor} is called directly with a
 * hand-built {@code HaltMessage} -- no Kafka anywhere in this test -- and the resulting email is captured
 * by an embedded fake SMTP server (GreenMail) instead of a real mail relay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HaltEventProcessorIntegrationTest {

    private static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void overrideInfraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", ServerSetupTest.SMTP::getPort);
        // No real Kafka broker in this test at all -- HaltEventProcessor.process() is called directly.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
    }

    @Autowired
    private HaltEventProcessor processor;

    @BeforeEach
    void startFakeSmtp() {
        GREEN_MAIL.start();
    }

    @AfterEach
    void stopFakeSmtp() {
        GREEN_MAIL.stop();
    }

    @Test
    void sendsTraditionalHaltEmailForImmediateHalt() throws Exception {
        HaltMessage message = HaltMessage.builder()
                .haltId("H-1")
                .symbol("ABC")
                .issueName("ABC Corp")
                .listingMarket("TSE")
                .allIssue("false")
                .haltTime("2026-08-25T09:30:00.000")
                .haltReasonDescription("Regulatory Concern")
                .haltReasonCode("REG")
                .command(Command.SUBMIT_IMMEDIATE_HALT)
                .state(State.ACTIVE_REG_HALT)
                .subState(SubState.Halt_Sent)
                .idempotencyKey("idem-traditional-1")
                .build();

        processor.process(message);

        // GreenMail stores one copy per SMTP envelope recipient -- this send has a To and a Bcc, so one
        // email means two stored copies, not one.
        GREEN_MAIL.waitForIncomingEmail(5_000, 2);
        MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
        assertThat(received).hasSize(2);

        MimeMessage email = received[0];
        assertThat(email.getSubject()).isEqualTo(
                "Canadian Investment Regulatory Organization Trading Halt / Suspension de la négociation "
                        + "par l’Organisme canadien de réglementation des investissements");
        assertThat(email.getAllRecipients()).extracting(Object::toString).contains("surveillance@ciro.ca");
        assertThat(email.getFrom()).extracting(Object::toString).containsExactly("halts@ciro.ca");

        String body = (String) email.getContent();
        assertThat(body).contains("ABC Corp").contains(">ABC<").contains("No/Non").contains("09:30:00 AM");
    }

    @Test
    void sendsNothingForDraftHalt() {
        HaltMessage message = HaltMessage.builder()
                .haltId("H-2")
                .command(Command.DRAFT_HALT)
                .state(State.DRAFT_REG_HALT)
                .subState(SubState.Halt_Drafted)
                .build();

        processor.process(message);

        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    @Test
    void deduplicatesRedeliveryOfTheSameIdempotencyKey() throws Exception {
        HaltMessage message = HaltMessage.builder()
                .haltId("H-3")
                .symbol("XYZ")
                .issueName("XYZ Inc")
                .listingMarket("CSX")
                .allIssue("true")
                .haltTime("2026-08-25T10:00:00.000")
                .haltReasonDescription("Trading Halt")
                .haltReasonCode("GEN")
                .command(Command.SUBMIT_IMMEDIATE_HALT)
                .state(State.ACTIVE_REG_HALT)
                .subState(SubState.Halt_Sent)
                .idempotencyKey("idem-dup-1")
                .build();

        processor.process(message);
        processor.process(message);

        // One actual send (To + Bcc = 2 stored copies in GreenMail). If dedup had failed, the second
        // process() call would have sent again and this would be 4, not 2.
        GREEN_MAIL.waitForIncomingEmail(5_000, 2);
        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(2);
    }
}
