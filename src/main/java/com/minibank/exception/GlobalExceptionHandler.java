package com.minibank.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.minibank.web.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({AccountNotFoundException.class, UserNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({InsufficientFundsException.class, InvalidAmountException.class,
            SameAccountTransferException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccountAccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleConflict(UserAlreadyExistsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        StringBuilder message = new StringBuilder();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            if (message.length() > 0) {
                message.append("; ");
            }
            message.append(error.getField()).append(": ").append(error.getDefaultMessage());
        }
        return buildResponse(HttpStatus.BAD_REQUEST, message.toString(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Request body is missing or not valid JSON", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
