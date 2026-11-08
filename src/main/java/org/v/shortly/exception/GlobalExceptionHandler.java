package org.v.shortly.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

@RestControllerAdvice                             // applies to ALL controllers: one central place for error handling
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)    // runs whenever any controller/service throws this exception
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        // ProblemDetail = the standard (RFC 7807) JSON error format; the status code is set from it automatically
    }

    @ExceptionHandler(ExpiredLinkException.class)
    public ProblemDetail handleExpired(ExpiredLinkException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());   // 410 Gone
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)   // thrown when @Valid fails
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())   // "url: must start with http..."
                .collect(Collectors.joining("; "));                      // join several errors into one text
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);   // 400
    }
}