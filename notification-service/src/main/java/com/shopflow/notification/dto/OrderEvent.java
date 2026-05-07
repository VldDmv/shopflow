package com.shopflow.notification.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderEvent(
        Long orderId,
        Long userId,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        String status,
        LocalDateTime createdAt
) {
}
