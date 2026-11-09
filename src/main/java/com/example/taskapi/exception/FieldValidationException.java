package com.example.taskapi.exception;

/**
 * A validation failure on one request field that Bean Validation annotations cannot express
 * (for example the password's byte length). Returned as a 400 with the same "errors" map
 * as annotation-based validation.
 */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
