package org.v.shortly.dto;

import jakarta.validation.constraints.*;                      // @NotBlank, @Size, @Pattern, @Min, @Max

public record ShortenRequest(
        @NotBlank(message = "url must not be empty")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        @Pattern(regexp = "^https?://\\S+$",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "url must start with http:// or https:// and contain no spaces")
        String url,

        @Pattern(regexp = "^[A-Za-z0-9_-]{6,10}$",                 // 6-10 chars: letters, digits, _ or -
                message = "customAlias must be 6-10 characters: letters, digits, _ or -")
        String customAlias,                                        // optional: null means "generate one for me"
        // (@Pattern treats null as valid, so skipping it is fine)

        @Min(value = 1, message = "ttlDays must be at least 1")
        @Max(value = 365, message = "ttlDays must be at most 365")
        Integer ttlDays                                            // Integer (not int) so it can be null = "never expires"
) {}