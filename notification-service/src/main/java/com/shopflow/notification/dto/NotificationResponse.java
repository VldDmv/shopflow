package com.shopflow.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long userId,
        Long orderId,
        String message,
        LocalDateTime createdAt
) {
}
