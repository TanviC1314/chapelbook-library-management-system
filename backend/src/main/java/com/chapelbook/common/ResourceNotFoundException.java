package com.chapelbook.common;

/** The requested thing does not exist (or is not visible to the caller). Becomes HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
