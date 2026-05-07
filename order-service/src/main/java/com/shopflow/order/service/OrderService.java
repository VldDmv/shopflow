package com.shopflow.order.service;

import com.shopflow.order.dto.CreateOrderRequest;
import com.shopflow.order.dto.OrderEvent;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.entity.Order;
import com.shopflow.order.kafka.OrderEventProducer;
import com.shopflow.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer eventProducer;

    public OrderService(OrderRepository orderRepository, OrderEventProducer eventProducer) {
        this.orderRepository = orderRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setUserId(request.userId());
        order.setProductName(request.productName());
        order.setQuantity(request.quantity());
        order.setTotalPrice(request.totalPrice());
        Order saved = orderRepository.save(order);

        try {
            eventProducer.publish(new OrderEvent(
                    saved.getId(), saved.getUserId(), saved.getProductName(),
                    saved.getQuantity(), saved.getTotalPrice(),
                    saved.getStatus().name(), saved.getCreatedAt()
            ));
        } catch (Exception e) {
            System.out.println("Kafka unavailable, order saved without event: " + e.getMessage());
        }

        return toResponse(saved);
    }

    public OrderResponse getOrderById(Long id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
    }

    public List<OrderResponse> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getUserId(), o.getProductName(),
                o.getQuantity(), o.getTotalPrice(), o.getStatus().name(), o.getCreatedAt());
    }
}