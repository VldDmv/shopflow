package com.shopflow.order.service;

import com.shopflow.order.dto.CreateOrderRequest;
import com.shopflow.order.dto.OrderEvent;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.entity.Order;
import com.shopflow.order.kafka.OrderEventProducer;
import com.shopflow.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventProducer eventProducer;
    @InjectMocks
    private OrderService orderService;

    private Order buildOrder(Long id) {
        Order o = new Order();
        o.setUserId(1L);
        o.setProductName("MacBook Pro");
        o.setQuantity(1);
        o.setTotalPrice(new BigDecimal("2499.99"));
        // set id via reflection since there's no setter
        try {
            var f = Order.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(o, id);
        } catch (Exception ignored) {
        }
        return o;
    }

    @Test
    void createOrder_savesOrderAndPublishesEvent() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, "MacBook Pro", 1, new BigDecimal("2499.99"));
        Order saved = buildOrder(1L);
        when(orderRepository.save(any())).thenReturn(saved);

        OrderResponse response = orderService.createOrder(request);

        verify(orderRepository).save(any(Order.class));
        verify(eventProducer).publish(any(OrderEvent.class));
        assertThat(response.productName()).isEqualTo("MacBook Pro");
        assertThat(response.status()).isEqualTo("PLACED");
    }

    @Test
    void createOrder_savesOrder_whenKafkaUnavailable() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L, "MacBook Pro", 1, new BigDecimal("2499.99"));
        Order saved = buildOrder(1L);
        when(orderRepository.save(any())).thenReturn(saved);
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(eventProducer).publish(any());

        // should NOT throw — graceful degradation
        assertThatCode(() -> orderService.createOrder(request))
                .doesNotThrowAnyException();

        verify(orderRepository).save(any());
    }

    @Test
    void getOrderById_returnsOrder() {
        Order order = buildOrder(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

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

        List<OrderResponse> result = orderService.getOrdersByUserId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).productName()).isEqualTo("MacBook Pro");
    }
}