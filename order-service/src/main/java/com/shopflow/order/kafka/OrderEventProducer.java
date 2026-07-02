package com.shopflow.order.kafka;

import com.shopflow.order.dto.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);
    public static final String TOPIC = "order-events";

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, OrderEvent>> publish(OrderEvent event) {
        return kafkaTemplate.send(TOPIC, String.valueOf(event.orderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event for orderId={}: {}", event.orderId(), ex.getMessage());
                    } else {
                        log.info("Published order event: orderId={}, topic={}, partition={}",
                                event.orderId(), TOPIC,
                                result.getRecordMetadata().partition());
                    }
                });
    }
}