package com.skypath.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * A complete, valid trip from an origin to a destination: an ordered list of
 * flight legs, the connection layovers between them, and the aggregate totals
 * used for display and ranking.
 *
 * <p>For a direct flight {@code segments} has one element and {@code layovers}
 * is empty; each additional stop adds one segment and one layover. Totals are
 * computed once at assembly time (in the search layer) so this type stays a
 * pure, immutable value. The segment and layover lists are defensively copied
 * to guarantee immutability.
 *
 * @param segments      ordered flight legs (never empty)
 * @param layovers      connection gaps between consecutive legs
 * @param totalDuration total travel time, first departure to last arrival
 * @param totalPrice    sum of all segment fares
 */
public record Itinerary(
        List<Flight> segments,
        List<Layover> layovers,
        Duration totalDuration,
        BigDecimal totalPrice
) {
    public Itinerary {
        segments = List.copyOf(segments);
        layovers = List.copyOf(layovers);
    }

    /** Number of stops: 0 for a direct flight, 1 or 2 for connections. */
    public int stops() {
        return segments.size() - 1;
    }

    /** Origin airport code of the whole itinerary. */
    public String origin() {
        return segments.getFirst().origin();
    }

    /** Final destination airport code of the whole itinerary. */
    public String destination() {
        return segments.getLast().destination();
    }

    /** Departure instant of the first leg. */
    public ZonedDateTime departureTime() {
        return segments.getFirst().departureTime();
    }

    /** Arrival instant of the final leg. */
    public ZonedDateTime arrivalTime() {
        return segments.getLast().arrivalTime();
    }
}
