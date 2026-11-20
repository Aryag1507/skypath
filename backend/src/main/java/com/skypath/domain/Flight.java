package com.skypath.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * A single scheduled flight &mdash; one directed edge in the flight graph,
 * and one leg of an itinerary.
 *
 * <p>Departure and arrival are stored as {@link ZonedDateTime}, i.e. already
 * resolved from the dataset's local wall-clock times against the relevant
 * airport's zone. Keeping zoned instants here means all downstream duration
 * math (flight time, layovers, total trip time) is correct regardless of time
 * zones or date-line crossings, and never has to re-interpret a naive string.
 *
 * <p>{@code price} uses {@link BigDecimal} to avoid floating-point rounding
 * error when summing fares across segments.
 *
 * @param flightNumber  carrier flight number, e.g. {@code SP101}
 * @param airline       operating airline
 * @param origin        departure airport IATA code
 * @param destination   arrival airport IATA code
 * @param departureTime zoned departure instant
 * @param arrivalTime   zoned arrival instant
 * @param price         fare for this flight
 * @param aircraft      aircraft type
 */
public record Flight(
        String flightNumber,
        String airline,
        String origin,
        String destination,
        ZonedDateTime departureTime,
        ZonedDateTime arrivalTime,
        BigDecimal price,
        String aircraft
) {
    /**
     * Wall-clock duration of this flight, correct across time zones because
     * both endpoints are absolute instants.
     */
    public Duration duration() {
        return Duration.between(departureTime, arrivalTime);
    }
}
