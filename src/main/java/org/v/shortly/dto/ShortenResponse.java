package org.v.shortly.dto;

import java.time.LocalDateTime;

public record ShortenResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        LocalDateTime createdAt,
        LocalDateTime expiresAt        // new: null if the link never expires
) {}