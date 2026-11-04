package com.example.taskapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import io.swagger.v3.oas.annotations.media.Schema;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleTaskNotFound(TaskNotFoundException exception) {

        return new ErrorResponse(
                404,
                exception.getMessage(),
                LocalDateTime.now()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleValidation(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        return new ValidationErrorResponse(
                400,
                "Validation failed",
                errors,
                LocalDateTime.now()
        );
    }

    @ExceptionHandler(InvalidQueryParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidQueryParameter(InvalidQueryParameterException exception) {

        return new ErrorResponse(
                400,
                exception.getMessage(),
                LocalDateTime.now()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException exception) {

        String message = "Invalid value '" + exception.getValue()
                + "' for parameter '" + exception.getName() + "'";

        Class<?> requiredType = exception.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            message += ". Allowed values: " + Arrays.toString(requiredType.getEnumConstants());
        }

        return new ErrorResponse(
                400,
                message,
                LocalDateTime.now()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException exception) {

        return new ErrorResponse(
                400,
                "Request body is malformed or contains invalid values",
                LocalDateTime.now()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {

        return new ErrorResponse(
                405,
                "Request method '" + exception.getMethod() + "' is not supported for this endpoint",
                LocalDateTime.now()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResource(NoResourceFoundException exception) {

        return new ErrorResponse(
                404,
                "No endpoint found for path: /" + exception.getResourcePath(),
                LocalDateTime.now()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception exception) {

        log.error("Unexpected error", exception);

        return new ErrorResponse(
                500,
                "An unexpected error occurred",
                LocalDateTime.now()
        );
    }

    @Schema(description = "Error body for every error except request body validation failures")
    public record ErrorResponse(
            @Schema(description = "HTTP status code", example = "404")
            int status,
            @Schema(description = "What went wrong", example = "Task not found with id: 42")
            String message,
            @Schema(description = "When the error occurred. Local date-time in Asia/Kolkata, without a UTC offset",
                    example = "2026-11-04T17:01:34.431")
            LocalDateTime timestamp
    ) {
    }

    @Schema(description = "Error body when request body validation fails (400)")
    public record ValidationErrorResponse(
            @Schema(description = "HTTP status code", example = "400")
            int status,
            @Schema(description = "Always \"Validation failed\"", example = "Validation failed")
            String message,
            @Schema(description = "Each invalid field mapped to its validation message",
                    example = "{\"title\": \"Title is required\"}")
            Map<String, String> errors,
            @Schema(description = "When the error occurred. Local date-time in Asia/Kolkata, without a UTC offset",
                    example = "2026-11-04T17:01:34.886")
            LocalDateTime timestamp
    ) {
    }
}