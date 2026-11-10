package org.v.shortly.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import java.io.IOException;

public final class HttpErrorWriter {
    private HttpErrorWriter() {}                              // utility class: nobody should create one

    // Filters run BEFORE Spring MVC, so our @RestControllerAdvice can't handle their errors.
    // We write the same JSON shape (RFC 7807) by hand. Only pass fixed texts without quotes as "detail".
    public static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());                   // e.g. 401
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
    }
}