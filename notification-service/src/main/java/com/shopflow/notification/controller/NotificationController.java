package com.shopflow.notification.controller;

import com.shopflow.notification.dto.NotificationResponse;
import com.shopflow.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "User notification history")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all notifications for a user (only the authenticated user's own)")
    public ResponseEntity<List<NotificationResponse>> getByUser(@RequestHeader("X-User-Id") Long authenticatedUserId,
                                                                @PathVariable Long userId) {
        if (!authenticatedUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another user's notifications");
        }
        return ResponseEntity.ok(notificationService.getByUserId(userId));
    }
}