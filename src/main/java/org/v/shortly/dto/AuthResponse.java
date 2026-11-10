package org.v.shortly.dto;

public record AuthResponse(String token, String tokenType, long expiresInSeconds) {}
// tokenType will be "Bearer", telling clients how to send it: Authorization: Bearer <token>