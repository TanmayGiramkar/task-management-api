package com.example.taskapi.exception;

public class InvalidQueryParameterException extends RuntimeException {

    public InvalidQueryParameterException(String message) {
        super(message);
    }
}