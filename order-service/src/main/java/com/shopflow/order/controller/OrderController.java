package com.shopflow.order.controller;

import com.shopflow.order.dto.CreateOrderRequest;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order management")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place a new order for the authenticated user")
    public ResponseEntity<OrderResponse> createOrder(@RequestHeader("X-User-Id") Long userId,
                                                     @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(userId, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the authenticated user's orders by ID")
    public ResponseEntity<OrderResponse> getOrder(@RequestHeader("X-User-Id") Long userId,
                                                  @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id, userId));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all orders for a user (only the authenticated user's own)")
    public ResponseEntity<List<OrderResponse>> getOrdersByUser(@RequestHeader("X-User-Id") Long authenticatedUserId,
                                                               @PathVariable Long userId) {
        if (!authenticatedUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another user's orders");
        }
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId));
    }
}