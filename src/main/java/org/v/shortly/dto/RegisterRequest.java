package org.v.shortly.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "email must not be empty")
        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "password must not be empty")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        // Max 72 because BCrypt only uses the first 72 bytes of a password. Longer input is ignored or rejected.
        String password
) {}