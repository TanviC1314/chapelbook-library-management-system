package com.chapelbook.common;

/** A business rule refuses the request (e.g. loan limit reached). Becomes HTTP 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
