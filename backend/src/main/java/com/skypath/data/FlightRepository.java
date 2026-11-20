package com.skypath.data;

import com.skypath.domain.Airport;
import com.skypath.domain.Flight;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory store and index of the flight network, populated once at startup.
 *
 * <p>Holds two indexes that back the search:
 * <ul>
 *   <li>an airport lookup by IATA code (also used to validate requests), and</li>
 *   <li>an adjacency list mapping each origin airport to its outbound flights
 *       &mdash; i.e. the graph the itinerary search traverses.</li>
 * </ul>
 *
 * <p>Outbound flight lists are pre-sorted by departure time so the search can
 * reason about them in chronological order. The dataset is small (hundreds of
 * flights) and read-only, so keeping it fully in memory is both sufficient and
 * simple.
 */
@Repository
public class FlightRepository implements AirportCatalog {

    private static final Logger log = LoggerFactory.getLogger(FlightRepository.class);

    private final FlightDataLoader loader;

    private Map<String, Airport> airportsByCode = Map.of();
    private Map<String, List<Flight>> flightsByOrigin = Map.of();
    private int flightCount;

    public FlightRepository(FlightDataLoader loader) {
        this.loader = loader;
    }

    @PostConstruct
    void init() {
        FlightData data = loader.load();
        index(data);
        log.info("Indexed {} airports and {} flights ({} airports with departures)",
                airportsByCode.size(), flightCount, flightsByOrigin.size());
    }

    /** Builds the lookup and adjacency indexes from loaded data. */
    void index(FlightData data) {
        this.airportsByCode = data.airports().stream()
                .collect(Collectors.toUnmodifiableMap(Airport::code, a -> a));

        this.flightsByOrigin = data.flights().stream()
                .collect(Collectors.groupingBy(Flight::origin));
        // Sort each origin's departures chronologically and make them immutable.
        this.flightsByOrigin = this.flightsByOrigin.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .sorted(Comparator.comparing(Flight::departureTime))
                                .toList()));

        this.flightCount = data.flights().size();
    }

    /** Looks up an airport by IATA code, if present. */
    @Override
    public Optional<Airport> findAirport(String code) {
        return Optional.ofNullable(airportsByCode.get(code));
    }

    /** Whether an airport with the given IATA code exists in the dataset. */
    @Override
    public boolean hasAirport(String code) {
        return airportsByCode.containsKey(code);
    }

    /** All outbound flights from the given airport, sorted by departure time. */
    public List<Flight> flightsFrom(String originCode) {
        return flightsByOrigin.getOrDefault(originCode, List.of());
    }

    public int airportCount() {
        return airportsByCode.size();
    }

    public int flightCount() {
        return flightCount;
    }
}
