package com.skypath.api.dto;

import java.math.BigDecimal;

/**
 * One flight leg as presented to the client. Times are ISO-8601 strings with
 * the airport's local offset (e.g. {@code 2024-03-15T08:30:00-04:00}) so the
 * frontend can display local times without any timezone computation of its own.
 */
public record SegmentResponse(
        String flightNumber,
        String airline,
        String origin,
        String destination,
        String departureTime,
        String arrivalTime,
        long durationMinutes,
        BigDecimal price,
        String aircraft
) {
}
