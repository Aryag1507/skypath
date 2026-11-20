package com.skypath.api.dto;

/**
 * Structured error body returned for failed requests, so the frontend can show
 * a clear message rather than parsing a stack trace.
 *
 * @param status  HTTP status code
 * @param error   short reason phrase (e.g. {@code "Bad Request"})
 * @param message human-readable explanation of what was wrong
 */
public record ErrorResponse(
        int status,
        String error,
        String message
) {
}
