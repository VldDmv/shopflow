package com.shopflow.order.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.order.dto.OrderEvent;
import com.shopflow.order.kafka.OrderEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Relays staged outbox events to Kafka. Events are published in insertion
 * order; on the first failure the run stops and the remaining events are
 * retried on the next tick, so Kafka being down only delays delivery
 * (at-least-once) instead of losing events.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outboxRepository;
    private final OrderEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository outboxRepository,
                           OrderEventProducer eventProducer,
                           ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.eventProducer = eventProducer;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.publish-interval-ms:2000}",
            initialDelayString = "${outbox.initial-delay-ms:5000}")
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByIdAsc();
        for (OutboxEvent outboxEvent : pending) {
            try {
                OrderEvent event = objectMapper.readValue(outboxEvent.getPayload(), OrderEvent.class);
                eventProducer.publish(event).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                outboxEvent.markPublished();
                outboxRepository.save(outboxEvent);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Outbox publish failed for event id={} (orderId={}), will retry: {}",
                        outboxEvent.getId(), outboxEvent.getAggregateId(), e.getMessage());
                return;
            }
        }
    }
}
