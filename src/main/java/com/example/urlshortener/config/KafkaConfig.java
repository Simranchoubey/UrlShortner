package com.example.urlshortener.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.example.urlshortener.messaging.ClickEventMessage;

/**
 * Kafka wiring for the Phase 8 click-analytics transport.
 *
 * <p>Producer and consumer both use JSON serialization of the
 * {@link ClickEventMessage} (key = short code {@link String}, value = message
 * JSON). All connection settings derive from {@link KafkaProperties}, bound from
 * environment variables, so production infrastructure is never hardcoded (§1).
 *
 * <p>The listener container factory auto-starts only when {@code app.kafka.enabled}
 * is true. When disabled (e.g. the {@code test} profile substitutes a fake
 * publisher and no broker is expected) the consumer container is never started, so
 * the application runs cleanly without a broker.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {

    private final KafkaProperties kafka;

    public KafkaConfig(KafkaProperties kafka) {
        this.kafka = kafka;
    }

    /** Producer-side JSON serialization of {@link ClickEventMessage}. */
    @Bean
    public ProducerFactory<String, ClickEventMessage> clickEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // A short bound so an unreachable broker cannot hang a redirect thread.
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 2000);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Consumer-side JSON deserialization. The target type is inferred by the
     * {@link JsonDeserializer} from the {@code @KafkaListener} method parameter,
     * so only the trusted package needs declaring here.
     */
    @Bean
    public ConsumerFactory<String, ClickEventMessage> clickEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafka.consumerGroup());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, ClickEventMessage.class.getPackageName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /** Template used by the {@code KafkaClickEventPublisher} for fire-and-forget sends. */
    @Bean
    public KafkaTemplate<String, ClickEventMessage> kafkaTemplate(
            ProducerFactory<String, ClickEventMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Listener container factory for the analytics consumer. Auto-start is gated on
     * {@code app.kafka.enabled} so the app can run with analytics disabled/without a
     * broker (e.g. the {@code test} profile) without the listener attempting to
     * connect.
     */
    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, ClickEventMessage>>
            kafkaListenerContainerFactory(ConsumerFactory<String, ClickEventMessage> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ClickEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);
        factory.setAutoStartup(kafka.enabled());
        return factory;
    }
}