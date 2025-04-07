package com.justjournal.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(Throwable cause) {
        super("Not found", cause);
    }

    public NotFoundException() {
        super("Not found");
    }
}