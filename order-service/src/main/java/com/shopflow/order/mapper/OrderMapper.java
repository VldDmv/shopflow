package com.shopflow.order.mapper;

import com.shopflow.order.dto.OrderEvent;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.entity.Order;
import com.shopflow.order.entity.OrderStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "status", source = "status", qualifiedByName = "statusToString")
    OrderResponse toResponse(Order order);

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "status", source = "status", qualifiedByName = "statusToString")
    OrderEvent toEvent(Order order);

    @Named("statusToString")
    default String statusToString(OrderStatus status) {
        return status == null ? null : status.name();
    }
}