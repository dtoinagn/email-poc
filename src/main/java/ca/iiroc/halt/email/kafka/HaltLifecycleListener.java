package ca.iiroc.halt.email.kafka;

import ca.ciro.halt.mapper.RegHaltMessageMapper;
import ca.ciro.halt.model.HaltMessage;
import ca.ciro.halt.model.RegHaltMessageOuterClass;
import ca.iiroc.halt.email.HaltEventProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Thin by design: deserialize (handled by {@link KafkaConsumerConfig}'s consumer factory), map to the
 * plain {@code HaltMessage} model via the shared lib's mapper, and delegate. No routing, formatting, or
 * mail logic lives here — see {@link HaltEventProcessor} and design doc §08/§11.
 */
@Component
@RequiredArgsConstructor
public class HaltLifecycleListener {

    private final HaltEventProcessor processor;

    @KafkaListener(
            topics = "${email-service.kafka.topic:halt.lifecycle}",
            containerFactory = "haltLifecycleContainerFactory")
    public void onMessage(RegHaltMessageOuterClass.RegHaltMessage regHaltMessage) {
        HaltMessage haltMessage = RegHaltMessageMapper.INSTANCE.toHaltMessage(regHaltMessage);
        processor.process(haltMessage);
    }
}
