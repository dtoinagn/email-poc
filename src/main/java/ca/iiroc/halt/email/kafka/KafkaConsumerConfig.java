package ca.iiroc.halt.email.kafka;

import ca.ciro.halt.model.RegHaltMessageOuterClass;
import ca.ciro.halt.serialization.HaltProtobufDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * The only Kafka-transport wiring in the service: deserialize the wire-format {@code RegHaltMessage}
 * protobuf (via the shared lib's {@link HaltProtobufDeserializer}), commit the offset only after a
 * message is fully handled ({@link ContainerProperties.AckMode#RECORD}), and retry a processing failure
 * with backoff before handing it to {@link HaltEmailFailureRecoverer} — see design doc §02 and §07.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, RegHaltMessageOuterClass.RegHaltMessage> haltLifecycleConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, HaltProtobufDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RegHaltMessageOuterClass.RegHaltMessage> haltLifecycleContainerFactory(
            ConsumerFactory<String, RegHaltMessageOuterClass.RegHaltMessage> haltLifecycleConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, RegHaltMessageOuterClass.RegHaltMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(haltLifecycleConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new HaltEmailFailureRecoverer(),
                new FixedBackOff(2000L, 2L)));
        return factory;
    }
}
