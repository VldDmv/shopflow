package com.shopflow.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        Long userId,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        String status,
        LocalDateTime createdAt
) {
}
