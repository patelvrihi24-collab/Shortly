package org.v.shortly.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

@RestControllerAdvice                                  // applies to ALL controllers: one central place for error handling
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)         // runs whenever any controller or service throws this exception
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());           // 404
    }

    @ExceptionHandler(ExpiredLinkException.class)
    public ProblemDetail handleExpired(ExpiredLinkException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());                // 410: existed, now gone
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ProblemDetail handleAliasTaken(AliasAlreadyExistsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());            // 409: valid request, conflicts with current state
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailTaken(EmailAlreadyExistsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());            // 409
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleBadCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());        // 401: "I don't know who you are"
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)    // thrown when @Valid fails on a request body
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())     // "url: must start with http..."
                .collect(Collectors.joining("; "));                        // join several errors into one text
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);                 // 400: malformed request
    }
}