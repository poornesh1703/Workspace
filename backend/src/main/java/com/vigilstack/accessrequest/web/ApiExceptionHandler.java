package com.vigilstack.accessrequest.web;

import com.vigilstack.accessrequest.service.NotFoundException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns Java exceptions into clean JSON errors like {"error": "..."} with the right HTTP status. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)   // validation failed -> 400
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .collect(Collectors.joining(" "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)   // bad JSON or unknown system -> 400
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or invalid.");
    }

    @ExceptionHandler(NotFoundException.class)                 // unknown id -> 404
    public ResponseEntity<Map<String, String>> notFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)             // already approved/rejected -> 409
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
