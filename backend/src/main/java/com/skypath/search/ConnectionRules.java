package com.skypath.search;

import com.skypath.data.AirportCatalog;
import com.skypath.domain.Airport;
import com.skypath.domain.Flight;
import com.skypath.domain.Layover;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates the rules that decide whether two consecutive flights form a
 * legal connection. Pure and side-effect free &mdash; given an arriving flight
 * and a candidate departing flight it returns a verdict &mdash; so it can be
 * exhaustively unit-tested in isolation from the graph search.
 *
 * <p>Thresholds mirror the specification directly:
 * <ul>
 *   <li>minimum layover: 45 min domestic, 90 min international (both inclusive)</li>
 *   <li>maximum layover: 6 hours (inclusive)</li>
 *   <li>the next flight must depart from the airport the previous one arrived at</li>
 * </ul>
 *
 * <p>Because flights carry absolute {@code ZonedDateTime} instants, all layover
 * math here is timezone-correct without any special handling.
 */
@Component
public class ConnectionRules {

    static final Duration MIN_LAYOVER_DOMESTIC = Duration.ofMinutes(45);
    static final Duration MIN_LAYOVER_INTERNATIONAL = Duration.ofMinutes(90);
    static final Duration MAX_LAYOVER = Duration.ofHours(6);

    private final AirportCatalog airports;

    public ConnectionRules(AirportCatalog airports) {
        this.airports = airports;
    }

    /**
     * Whether a passenger can connect from {@code arriving} onto
     * {@code departing}: same airport, departure after arrival, and the layover
     * within the inclusive [minimum, maximum] window for its domestic/
     * international classification.
     */
    public boolean isValidConnection(Flight arriving, Flight departing) {
        if (!arriving.destination().equals(departing.origin())) {
            return false; // cannot change airports during a layover
        }
        Duration layover = layoverBetween(arriving, departing);
        if (layover.isNegative()) {
            return false; // the onward flight has already departed
        }
        Duration minimum = isInternationalConnection(arriving, departing)
                ? MIN_LAYOVER_INTERNATIONAL
                : MIN_LAYOVER_DOMESTIC;
        return layover.compareTo(minimum) >= 0 && layover.compareTo(MAX_LAYOVER) <= 0;
    }

    /** The wall-clock gap between landing and the next departure. */
    public Duration layoverBetween(Flight arriving, Flight departing) {
        return Duration.between(arriving.arrivalTime(), departing.departureTime());
    }

    /**
     * A connection is domestic when every airport involved in both flights is
     * in the same country. Any unknown country is treated as international
     * (the stricter case).
     */
    public boolean isDomesticConnection(Flight arriving, Flight departing) {
        Set<String> countries = new HashSet<>();
        countries.add(countryOf(arriving.origin()));
        countries.add(countryOf(arriving.destination()));
        countries.add(countryOf(departing.origin()));
        countries.add(countryOf(departing.destination()));
        return countries.size() == 1 && !countries.contains(null);
    }

    public boolean isInternationalConnection(Flight arriving, Flight departing) {
        return !isDomesticConnection(arriving, departing);
    }

    /**
     * Builds the {@link Layover} record for a (valid) connection, capturing the
     * connection airport, gap duration and domestic/international classification
     * so the search need not recompute them.
     */
    public Layover toLayover(Flight arriving, Flight departing) {
        return new Layover(
                arriving.destination(),
                layoverBetween(arriving, departing),
                isInternationalConnection(arriving, departing));
    }

    private String countryOf(String airportCode) {
        return airports.findAirport(airportCode)
                .map(Airport::country)
                .orElse(null);
    }
}
