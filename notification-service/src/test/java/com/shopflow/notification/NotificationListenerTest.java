package com.shopflow.notification;

import com.shopflow.notification.dto.OrderEvent;
import com.shopflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"order-events"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9098", "port=9098"}
)
@DirtiesContext
@ActiveProfiles("test")
@Testcontainers
class NotificationListenerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void whenOrderEventReceived_shouldSaveNotification() throws InterruptedException {
        OrderEvent event = new OrderEvent(
                42L, 1L, "iPhone 15", 2,
                new BigDecimal("1999.98"), "PLACED", LocalDateTime.now()
        );

        kafkaTemplate.send("order-events", event);
        TimeUnit.SECONDS.sleep(2);

        var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(1L);
        assertThat(notifications).isNotEmpty();
        assertThat(notifications.get(0).getMessage()).contains("iPhone 15");
    }
}