package org.v.shortly.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password   // cap the size so nobody can make us hash a 10 MB "password"
) {}