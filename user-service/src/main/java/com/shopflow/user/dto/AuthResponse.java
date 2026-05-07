package com.shopflow.user.dto;

public record AuthResponse(String token, String username, String role) {
}
