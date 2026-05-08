package com.shopflow.notification.mapper;

import com.shopflow.notification.dto.NotificationResponse;
import com.shopflow.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}