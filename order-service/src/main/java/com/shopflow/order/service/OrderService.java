package com.shopflow.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.order.client.UserClient;
import com.shopflow.order.dto.CreateOrderRequest;
import com.shopflow.order.dto.OrderEvent;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.entity.Order;
import com.shopflow.order.mapper.OrderMapper;
import com.shopflow.order.outbox.OutboxEvent;
import com.shopflow.order.outbox.OutboxEventRepository;
import com.shopflow.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class OrderService {

    public static final String ORDER_PLACED_EVENT = "ORDER_PLACED";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final OrderMapper orderMapper;
    private final UserClient userClient;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxRepository,
                        OrderMapper orderMapper,
                        UserClient userClient,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.orderMapper = orderMapper;
        this.userClient = userClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {
        if (!userClient.userExists(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setProductName(request.productName());
        order.setQuantity(request.quantity());
        order.setTotalPrice(request.totalPrice());
        Order saved = orderRepository.save(order);

        // Transactional outbox: the event is committed atomically with the
        // order and relayed to Kafka by OutboxPublisher, so a broker outage
        // delays the notification instead of losing it
        OrderEvent event = orderMapper.toEvent(saved);
        outboxRepository.save(new OutboxEvent(saved.getId(), ORDER_PLACED_EVENT, toJson(event)));

        return orderMapper.toResponse(saved);
    }

    public OrderResponse getOrderById(Long id, Long requesterId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        if (!order.getUserId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order belongs to another user");
        }
        return orderMapper.toResponse(order);
    }

    public List<OrderResponse> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    private String toJson(OrderEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order event", e);
        }
    }
}