package com.shopflow.notification.service;

import com.shopflow.notification.dto.NotificationResponse;
import com.shopflow.notification.dto.OrderEvent;
import com.shopflow.notification.entity.Notification;
import com.shopflow.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void processOrderEvent(OrderEvent event) {
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
        notificationRepository.save(notification);

        log.info("Notification saved for userId={}: {}", event.userId(), message);
    }

    public List<NotificationResponse> getByUserId(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationResponse(
                        n.getId(), n.getUserId(), n.getOrderId(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}