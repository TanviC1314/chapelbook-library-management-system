package com.chapelbook.common;

/** The request is valid but clashes with current state (duplicate, already booked...). Becomes HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
