package com.shopflow.order.service;

import com.shopflow.order.client.UserClient;
import com.shopflow.order.dto.CreateOrderRequest;
import com.shopflow.order.dto.OrderEvent;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.entity.Order;
import com.shopflow.order.kafka.OrderEventProducer;
import com.shopflow.order.mapper.OrderMapper;
import com.shopflow.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventProducer eventProducer;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private UserClient userClient;
    @InjectMocks
    private OrderService orderService;

    private Order buildOrder(Long id) {
        Order o = new Order();
        o.setUserId(1L);
        o.setProductName("MacBook Pro");
        o.setQuantity(1);
        o.setTotalPrice(new BigDecimal("2499.99"));
        try {
            var f = Order.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(o, id);
        } catch (Exception ignored) {
        }
        return o;
    }

    private OrderResponse stubResponse(Long id) {
        return new OrderResponse(id, 1L, "MacBook Pro", 1,
                new BigDecimal("2499.99"), "PLACED", LocalDateTime.now());
    }

    @Test
    void createOrder_savesOrderAndPublishesEvent() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, "MacBook Pro", 1, new BigDecimal("2499.99"));
        Order saved = buildOrder(1L);
        when(userClient.userExists(1L)).thenReturn(true);
        when(orderRepository.save(any())).thenReturn(saved);
        when(orderMapper.toEvent(saved)).thenReturn(mock(OrderEvent.class));
        when(orderMapper.toResponse(saved)).thenReturn(stubResponse(1L));

        OrderResponse response = orderService.createOrder(request);

        verify(orderRepository).save(any(Order.class));
        verify(eventProducer).publish(any(OrderEvent.class));
        assertThat(response.productName()).isEqualTo("MacBook Pro");
        assertThat(response.status()).isEqualTo("PLACED");
    }

    @Test
    void createOrder_throws_whenUserNotFound() {
        CreateOrderRequest request = new CreateOrderRequest(
                42L, "MacBook Pro", 1, new BigDecimal("2499.99"));
        when(userClient.userExists(42L)).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("42");

        verify(orderRepository, never()).save(any());
        verify(eventProducer, never()).publish(any());
    }

    @Test
    void createOrder_savesOrder_whenKafkaUnavailable() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, "MacBook Pro", 1, new BigDecimal("2499.99"));
        Order saved = buildOrder(1L);
        when(userClient.userExists(1L)).thenReturn(true);
        when(orderRepository.save(any())).thenReturn(saved);
        when(orderMapper.toEvent(saved)).thenReturn(mock(OrderEvent.class));
        when(orderMapper.toResponse(saved)).thenReturn(stubResponse(1L));
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(eventProducer).publish(any());

        assertThatCode(() -> orderService.createOrder(request))
                .doesNotThrowAnyException();

        verify(orderRepository).save(any());
    }

    @Test
    void getOrderById_returnsOrder() {
        Order order = buildOrder(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(stubResponse(1L));

        OrderResponse response = orderService.getOrderById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.productName()).isEqualTo("MacBook Pro");
    }

    @Test
    void getOrderById_throwsException_whenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getOrdersByUserId_returnsList() {
        Order o1 = buildOrder(1L);
        Order o2 = buildOrder(2L);
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(o1, o2));
        when(orderMapper.toResponse(any(Order.class)))
                .thenReturn(stubResponse(1L), stubResponse(2L));

        List<OrderResponse> result = orderService.getOrdersByUserId(1L);

        assertThat(result).hasSize(2);
    }
}