package com.shopflow.notification.config;

import com.shopflow.notification.dto.OrderEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory() {
        // ErrorHandlingDeserializer turns a malformed payload into a handled
        // error instead of an endless redelivery loop blocking the partition
        ErrorHandlingDeserializer<OrderEvent> deserializer =
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(OrderEvent.class, false));

        DefaultKafkaConsumerFactory<String, OrderEvent> factory = new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, "notification-group",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
                ),
                new StringDeserializer(),
                deserializer
        );

        ConcurrentKafkaListenerContainerFactory<String, OrderEvent> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(factory);
        containerFactory.setCommonErrorHandler(kafkaErrorHandler());
        return containerFactory;
    }

    /**
     * Retries a failed record 3 times with a 1s pause, then publishes it to
     * the dead-letter topic {@code order-events.DLT} so the partition keeps
     * moving and the poison message stays inspectable.
     */
    private DefaultErrorHandler kafkaErrorHandler() {
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        // deserialization failures carry the raw bytes; processing failures the parsed event
        templates.put(byte[].class, new KafkaTemplate<>(dltProducerFactory(ByteArraySerializer.class)));
        templates.put(Object.class, new KafkaTemplate<>(dltProducerFactory(JsonSerializer.class)));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    private <V> DefaultKafkaProducerFactory<String, V> dltProducerFactory(Class<?> valueSerializer) {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer
        ));
    }
}
