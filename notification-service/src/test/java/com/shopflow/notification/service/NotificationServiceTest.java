package com.shopflow.notification.service;

import com.shopflow.notification.dto.NotificationResponse;
import com.shopflow.notification.dto.OrderEvent;
import com.shopflow.notification.entity.Notification;
import com.shopflow.notification.mapper.NotificationMapper;
import com.shopflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void processOrderEvent_savesNotificationWithCorrectData() {
        OrderEvent event = new OrderEvent(
                3L, 1L, "MacBook Pro", 1, new BigDecimal("2499.99"),
                "PLACED", LocalDateTime.now()
        );

        notificationService.processOrderEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getOrderId()).isEqualTo(3L);
        assertThat(saved.getMessage()).contains("MacBook Pro");
        assertThat(saved.getMessage()).contains("2499.99");
        assertThat(saved.getMessage()).contains("Order #3");
    }

    @Test
    void processOrderEvent_messageContainsAllOrderDetails() {
        OrderEvent event = new OrderEvent(
                5L, 2L, "iPhone 15", 2, new BigDecimal("999.99"),
                "PLACED", LocalDateTime.now()
        );

        notificationService.processOrderEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        String message = captor.getValue().getMessage();
        assertThat(message).contains("Order #5");
        assertThat(message).contains("iPhone 15");
        assertThat(message).contains("2 x");
        assertThat(message).contains("999.99");
        assertThat(message).contains("PLACED");
    }

    @Test
    void getByUserId_returnsNotificationsForUser() {
        Notification n = new Notification();
        n.setUserId(1L);
        n.setOrderId(3L);
        n.setMessage("Order #3 confirmed: 1 x 'MacBook Pro' — Total: $2499.99 [PLACED]");

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(n));
        when(notificationMapper.toResponse(any(Notification.class)))
                .thenReturn(new NotificationResponse(1L, 1L, 3L,
                        "Order #3 confirmed: 1 x 'MacBook Pro' — Total: $2499.99 [PLACED]",
                        LocalDateTime.now()));

        List<NotificationResponse> result = notificationService.getByUserId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(1L);
        assertThat(result.get(0).orderId()).isEqualTo(3L);
        assertThat(result.get(0).message()).contains("MacBook Pro");
    }

    @Test
    void getByUserId_returnsEmptyList_whenNoNotifications() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(99L))
                .thenReturn(List.of());

        List<NotificationResponse> result = notificationService.getByUserId(99L);

        assertThat(result).isEmpty();
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(99L);
    }
}