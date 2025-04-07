package com.justjournal.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(Throwable cause) {
        super("Authentication required to access this resource", cause);
    }

    public UnauthorizedException() {
        super("Authentication required to access this resource");
    }
}