package org.v.shortly.dto;

import java.time.LocalDateTime;

public record UrlSummaryResponse(
        String shortCode, String shortUrl, String originalUrl,
        LocalDateTime createdAt, LocalDateTime expiresAt, long clickCount
) {}