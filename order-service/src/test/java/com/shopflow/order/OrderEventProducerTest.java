package com.shopflow.order;

import com.shopflow.order.dto.CreateOrderRequest;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.kafka.OrderEventProducer;
import com.shopflow.order.service.OrderService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {OrderEventProducer.TOPIC},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9099", "port=9099"}
)
@ActiveProfiles("test")
class OrderEventProducerTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void createOrder_shouldPersistAndPublishKafkaEvent() {
        // Arrange
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-group", "true", embeddedKafkaBroker);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, OrderEventProducer.TOPIC);

        // Act
        CreateOrderRequest request = new CreateOrderRequest(1L, "MacBook Pro", 1, new BigDecimal("2499.99"));
        OrderResponse response = orderService.createOrder(request);

        // Assert — order saved
        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo("PLACED");

        // Assert — event published to Kafka
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        consumer.close();
    }
}
