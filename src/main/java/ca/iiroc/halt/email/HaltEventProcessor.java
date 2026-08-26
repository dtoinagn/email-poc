package ca.iiroc.halt.email;

import ca.ciro.halt.model.HaltMessage;
import ca.iiroc.halt.email.idempotency.RecentSendGuard;
import ca.iiroc.halt.email.mail.EmailContent;
import ca.iiroc.halt.email.mail.HaltEmailSender;
import ca.iiroc.halt.email.routing.EmailTrigger;
import ca.iiroc.halt.email.routing.TriggerResolver;
import ca.iiroc.halt.email.template.EmailContentBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The one entry point for turning a halt.lifecycle event into a sent email — routing, idempotency,
 * rendering, and sending, with no Kafka type in its signature. {@code HaltLifecycleListener} is the only
 * class that knows about Kafka; it does nothing but deserialize, map, and call {@link #process}. See the
 * design doc §11 "Testing strategy" — that split is what lets most tests run without a broker.
 *
 * <p>Any exception thrown here propagates to the Kafka listener container, whose error handler retries
 * with backoff and, once exhausted, logs full context to the dedicated error log file (§07/§09) — there is
 * no database and no dead-letter topic in this design.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HaltEventProcessor {

    private final TriggerResolver triggerResolver;
    private final RecentSendGuard recentSendGuard;
    private final EmailContentBuilder contentBuilder;
    private final HaltEmailSender emailSender;

    public void process(HaltMessage message) {
        Optional<EmailTrigger> trigger = triggerResolver.resolve(message);
        if (trigger.isEmpty()) {
            log.debug("No email trigger for haltId={} command={} subState={}",
                    message.getHaltId(), message.getCommand(), message.getSubState());
            return;
        }

        String dedupeKey = dedupeKey(message, trigger.get());
        if (recentSendGuard.isDuplicate(dedupeKey)) {
            log.info("Skipping duplicate send for key={} trigger={}", dedupeKey, trigger.get());
            return;
        }

        EmailContent content = contentBuilder.build(trigger.get(), message);
        emailSender.send(content);
        recentSendGuard.markSent(dedupeKey);
    }

    private String dedupeKey(HaltMessage message, EmailTrigger trigger) {
        String idempotencyKey = message.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        return message.getHaltId() + ":" + trigger;
    }
}
