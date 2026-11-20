package com.skypath.api;

/**
 * Thrown when a search request is malformed or semantically invalid (bad
 * airport code, same origin and destination, unparseable date, unknown
 * airport). Mapped to HTTP 400 by {@link ApiExceptionHandler}.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
