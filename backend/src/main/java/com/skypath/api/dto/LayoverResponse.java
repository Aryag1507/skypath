package com.skypath.api.dto;

/**
 * A connection layover as presented to the client: the airport, its length in
 * minutes plus a human-friendly label (e.g. {@code "1h 30m"}), and whether the
 * connection was treated as international.
 */
public record LayoverResponse(
        String airportCode,
        long durationMinutes,
        String durationLabel,
        boolean international
) {
}
