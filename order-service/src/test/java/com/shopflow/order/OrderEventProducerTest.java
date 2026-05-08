package com.shopflow.order;

import com.shopflow.order.client.UserDto;
import com.shopflow.order.client.UserFeignClient;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {OrderEventProducer.TOPIC},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9099", "port=9099"}
)
@ActiveProfiles("test")
@Testcontainers
class OrderEventProducerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockBean
    private UserFeignClient userFeignClient;

    @Test
    void createOrder_shouldPersistAndPublishKafkaEvent() {
        when(userFeignClient.getUserById(anyLong()))
                .thenReturn(new UserDto(1L, "alice", "alice@shop.com", "USER"));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-group", "true", embeddedKafkaBroker);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, OrderEventProducer.TOPIC);

        CreateOrderRequest request = new CreateOrderRequest(1L, "MacBook Pro", 1, new BigDecimal("2499.99"));
        OrderResponse response = orderService.createOrder(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo("PLACED");

        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertThat(records.count()).isGreaterThanOrEqualTo(1);

        consumer.close();
    }
}