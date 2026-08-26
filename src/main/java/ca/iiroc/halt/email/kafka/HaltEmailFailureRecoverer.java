package ca.iiroc.halt.email.kafka;

import ca.ciro.halt.model.RegHaltMessageOuterClass;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

/**
 * Runs once a Kafka redelivery's retries are exhausted (see {@link KafkaConsumerConfig}). No database, no
 * dead-letter topic (design doc §07/§10) — this is the entire failure-handling story: a single structured
 * {@code ERROR} line to the dedicated {@code HALT_EMAIL_ERRORS} logger (bound to
 * {@code logs/halt-email-errors.log} in {@code logback-spring.xml}), after which the container commits
 * the offset like any other successfully handled record. Recovery is manual: read the log, resend.
 */
public class HaltEmailFailureRecoverer implements ConsumerRecordRecoverer {

    private static final Logger ERROR_LOG = LoggerFactory.getLogger("HALT_EMAIL_ERRORS");

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        String haltId = "unknown";
        String command = "unknown";
        String subState = "unknown";
        String symbol = "unknown";
        String listingMarket = "unknown";

        if (record.value() instanceof RegHaltMessageOuterClass.RegHaltMessage regHaltMessage) {
            haltId = regHaltMessage.getHaltId();
            command = String.valueOf(regHaltMessage.getCommand());
            subState = String.valueOf(regHaltMessage.getSubState());
            symbol = regHaltMessage.getSymbol();
            listingMarket = regHaltMessage.getListingMarket();
        }

        ERROR_LOG.error(
                "Failed to process halt.lifecycle record after retries exhausted: "
                        + "topic={} partition={} offset={} haltId={} command={} subState={} symbol={} listingMarket={}",
                record.topic(), record.partition(), record.offset(),
                haltId, command, subState, symbol, listingMarket,
                exception);
    }
}
