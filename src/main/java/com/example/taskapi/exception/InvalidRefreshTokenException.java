package com.example.taskapi.exception;

/**
 * The refresh token is unknown, expired or revoked. One message for all three, so a caller cannot
 * probe which tokens exist.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid, expired or revoked");
    }
}
