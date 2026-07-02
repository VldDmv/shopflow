package com.shopflow.order.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.order.dto.OrderEvent;
import com.shopflow.order.kafka.OrderEventProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxRepository;
    @Mock
    private OrderEventProducer eventProducer;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private OutboxEvent stubOutboxEvent(Long orderId) throws Exception {
        OrderEvent event = new OrderEvent(orderId, 1L, "MacBook Pro", 1,
                new BigDecimal("2499.99"), "PLACED", LocalDateTime.now());
        return new OutboxEvent(orderId, "ORDER_PLACED", objectMapper.writeValueAsString(event));
    }

    @Test
    void publishPending_publishesAndMarksEvents() throws Exception {
        OutboxEvent first = stubOutboxEvent(1L);
        OutboxEvent second = stubOutboxEvent(2L);
        when(outboxRepository.findTop50ByPublishedAtIsNullOrderByIdAsc())
                .thenReturn(List.of(first, second));
        when(eventProducer.publish(any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        outboxPublisher.publishPending();

        verify(eventProducer, times(2)).publish(any(OrderEvent.class));
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNotNull();
        verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    void publishPending_keepsEventUnpublished_whenKafkaFails() throws Exception {
        OutboxEvent first = stubOutboxEvent(1L);
        OutboxEvent second = stubOutboxEvent(2L);
        when(outboxRepository.findTop50ByPublishedAtIsNullOrderByIdAsc())
                .thenReturn(List.of(first, second));
        when(eventProducer.publish(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable")));

        outboxPublisher.publishPending();

        // publishing stops at the first failure — both events stay pending for the next tick
        verify(eventProducer, times(1)).publish(any(OrderEvent.class));
        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void publishPending_doesNothing_whenNoPendingEvents() {
        when(outboxRepository.findTop50ByPublishedAtIsNullOrderByIdAsc())
                .thenReturn(List.of());

        outboxPublisher.publishPending();

        verifyNoInteractions(eventProducer);
    }
}
