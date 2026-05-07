package com.shopflow.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotBlank String productName,
        @Positive int quantity,
        @Positive @NotNull BigDecimal totalPrice
) {
}
