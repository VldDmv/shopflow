package com.shopflow.order.service;

import com.shopflow.order.client.UserClient;
import com.shopflow.order.dto.CreateOrderRequest;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.entity.Order;
import com.shopflow.order.kafka.OrderEventProducer;
import com.shopflow.order.mapper.OrderMapper;
import com.shopflow.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderEventProducer eventProducer;
    private final OrderMapper orderMapper;
    private final UserClient userClient;

    public OrderService(OrderRepository orderRepository,
                        OrderEventProducer eventProducer,
                        OrderMapper orderMapper,
                        UserClient userClient) {
        this.orderRepository = orderRepository;
        this.eventProducer = eventProducer;
        this.orderMapper = orderMapper;
        this.userClient = userClient;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (!userClient.userExists(request.userId())) {
            throw new EntityNotFoundException("User not found: " + request.userId());
        }

        Order order = new Order();
        order.setUserId(request.userId());
        order.setProductName(request.productName());
        order.setQuantity(request.quantity());
        order.setTotalPrice(request.totalPrice());
        Order saved = orderRepository.save(order);

        try {
            eventProducer.publish(orderMapper.toEvent(saved));
        } catch (Exception e) {
            log.warn("Kafka publish failed, order persisted without event: {}", e.getMessage());
        }

        return orderMapper.toResponse(saved);
    }

    public OrderResponse getOrderById(Long id) {
        return orderRepository.findById(id)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
    }

    public List<OrderResponse> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }
}