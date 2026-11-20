package com.skypath.data.dto;

import java.math.BigDecimal;

/**
 * Raw flight entry as it appears in {@code flights.json}.
 *
 * <p>Times are left as raw ISO local date-time strings here (e.g.
 * {@code 2024-03-15T08:30:00}); the loader resolves them against the relevant
 * airport's time zone when mapping to the {@link com.skypath.domain.Flight}
 * domain type.
 */
public record FlightDto(
        String flightNumber,
        String airline,
        String origin,
        String destination,
        String departureTime,
        String arrivalTime,
        BigDecimal price,
        String aircraft
) {
}
