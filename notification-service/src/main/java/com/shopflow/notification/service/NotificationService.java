package com.shopflow.notification.service;

import com.shopflow.notification.dto.NotificationResponse;
import com.shopflow.notification.dto.OrderEvent;
import com.shopflow.notification.entity.Notification;
import com.shopflow.notification.mapper.NotificationMapper;
import com.shopflow.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    public void processOrderEvent(OrderEvent event) {
        // Kafka delivers at-least-once — skip events already processed
        if (notificationRepository.existsByOrderId(event.orderId())) {
            log.info("Duplicate order event skipped: orderId={}", event.orderId());
            return;
        }

        String message = String.format(
                Locale.US,
                "Order #%d confirmed: %d x '%s' — Total: $%.2f [%s]",
                event.orderId(), event.quantity(), event.productName(),
                event.totalPrice(), event.status()
        );

        Notification notification = new Notification();
        notification.setUserId(event.userId());
        notification.setOrderId(event.orderId());
        notification.setMessage(message);
        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException e) {
            // unique constraint on order_id closes the check-then-save race
            log.info("Duplicate order event skipped on insert: orderId={}", event.orderId());
            return;
        }

        log.info("Notification saved for userId={}: {}", event.userId(), message);
    }

    public List<NotificationResponse> getByUserId(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(notificationMapper::toResponse)
                .toList();
    }
}