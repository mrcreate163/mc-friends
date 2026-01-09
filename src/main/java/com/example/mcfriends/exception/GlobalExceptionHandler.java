package com.example.mcfriends.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

    public static class ErrorDetails {
        private LocalDateTime timestamp;
        private String message;
        private String details;

        public ErrorDetails(LocalDateTime timestamp, String message, String details) {
            this.timestamp = timestamp;
            this.message = message;
            this.details = details;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public String getMessage() { return message; }
        public String getDetails() { return details; }
    }

    private ResponseEntity<ErrorDetails> buildErrorResponse(String message, WebRequest request, HttpStatus status) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                message,
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, status);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        return buildErrorResponse(ex.getMessage(), request, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SelfFriendshipException.class)
    public ResponseEntity<ErrorDetails> handleSelfFriendship(SelfFriendshipException ex, WebRequest request) {
        return buildErrorResponse(ex.getMessage(), request, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FriendshipAlreadyExistsException.class)
    public ResponseEntity<ErrorDetails> handleFriendshipExists(FriendshipAlreadyExistsException ex, WebRequest request) {
        return buildErrorResponse(ex.getMessage(), request, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorDetails> handleGeneralRuntimeException(RuntimeException ex, WebRequest request) {
        return buildErrorResponse(ex.getMessage(), request, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> handleGlobalException(Exception ex, WebRequest request) {
        return buildErrorResponse("Произошла внутренняя ошибка сервера", request, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}