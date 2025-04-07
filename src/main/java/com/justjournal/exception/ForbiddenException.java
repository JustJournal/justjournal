package com.justjournal.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(Throwable cause) {
        super("Access denied", cause);
    }

    public ForbiddenException() {
        super("Access denied");
    }
}