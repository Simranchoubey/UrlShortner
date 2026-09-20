package com.example.urlshortener.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
import com.example.urlshortener.messaging.ClickEventPublisher;

/**
 * Kafka wiring for the Phase 8 click-analytics transport.
 *
 * <p>Producer and consumer both use JSON serialization of the
 * {@link ClickEventMessage} (key = short code {@link String}, value = message
 * JSON). All connection settings derive from {@link KafkaProperties}, bound from
 * environment variables, so production infrastructure is never hardcoded (§1).
 *
 * <p>Kafka is <b>disabled by default</b> ({@code app.kafka.enabled} defaults to
 * {@code false}). Only when explicitly enabled ({@code APP_KAFKA_ENABLED=true})
 * does this configuration create the producer/consumer beans or start the
 * listener container — so the application runs cleanly with no broker at all when
 * Kafka is off. When disabled, a no-op {@link ClickEventPublisher} is registered
 * so the redirect path still has a bean to inject (it simply publishes nothing).
 *
 * <p>Under the {@code test} profile a fake publisher is substituted so integration
 * tests run without a broker.
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {

    private final KafkaProperties kafka;

    public KafkaConfig(KafkaProperties kafka) {
        this.kafka = kafka;
    }

    /**
     * No-op publisher used whenever Kafka is disabled, so {@link UrlService} (and
     * anything else depending on {@link ClickEventPublisher}) always has a bean to
     * inject and never touches a broker. Disabled under the {@code test} profile,
     * which provides its own fake.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
    @Profile("!test")
    public ClickEventPublisher disabledClickEventPublisher() {
        return event -> {
            // Kafka is disabled; intentionally do nothing.
        };
    }

    /** Producer-side JSON serialization of {@link ClickEventMessage}. */
    @Bean
    @ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
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
    @ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
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
    @ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
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
    @ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
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