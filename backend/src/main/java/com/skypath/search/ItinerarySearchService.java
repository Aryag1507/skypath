package com.skypath.search;

import com.skypath.data.FlightRepository;
import com.skypath.domain.Flight;
import com.skypath.domain.Itinerary;
import com.skypath.domain.Layover;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds valid itineraries between two airports with a depth-limited DFS over
 * the flight graph.
 *
 * <p>Connections are capped at two stops (three legs), so a bounded DFS that
 * discards invalid connections is sufficient here &mdash; no weighted
 * shortest-path needed. The requested date constrains only the first leg's
 * departure; later connections may cross midnight within the layover limit.
 */
@Service
public class ItinerarySearchService {

    /** Maximum flight legs in an itinerary: a direct plus up to two stops. */
    static final int MAX_SEGMENTS = 3;

    private static final Comparator<Itinerary> BY_TRAVEL_TIME =
            Comparator.comparing(Itinerary::totalDuration)
                    .thenComparing(Itinerary::totalPrice)
                    .thenComparing(Itinerary::departureTime);

    private final FlightRepository flights;
    private final ConnectionRules rules;

    public ItinerarySearchService(FlightRepository flights, ConnectionRules rules) {
        this.flights = flights;
        this.rules = rules;
    }

    /**
     * Returns all valid itineraries from {@code origin} to {@code destination}
     * whose first flight departs on {@code date}, sorted by total travel time
     * (shortest first). Returns an empty list when origin and destination are
     * the same or no route exists.
     */
    public List<Itinerary> search(String origin, String destination, LocalDate date) {
        List<Itinerary> results = new ArrayList<>();
        if (origin.equals(destination)) {
            return results;
        }

        for (Flight first : flights.flightsFrom(origin)) {
            if (!first.departureTime().toLocalDate().equals(date)) {
                continue; // first leg must depart on the requested date
            }
            Deque<Flight> path = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            visited.add(origin);

            path.addLast(first);
            visited.add(first.destination());
            extend(path, visited, destination, results);
            path.removeLast();
            visited.remove(first.destination());
        }

        results.sort(BY_TRAVEL_TIME);
        return results;
    }

    /**
     * Depth-first extension of the current partial path. If the latest leg has
     * reached the destination, the path is recorded; otherwise, while under the
     * segment cap, every valid onward connection is explored.
     */
    private void extend(Deque<Flight> path, Set<String> visited,
                        String destination, List<Itinerary> results) {
        Flight last = path.getLast();
        if (last.destination().equals(destination)) {
            results.add(toItinerary(path));
            return;
        }
        if (path.size() >= MAX_SEGMENTS) {
            return; // cannot add another leg without exceeding the stop limit
        }

        for (Flight next : flights.flightsFrom(last.destination())) {
            String arrival = next.destination();
            if (visited.contains(arrival)) {
                continue; // don't loop back through an airport already used
            }
            if (!rules.isValidConnection(last, next)) {
                continue; // skip illegal connections
            }
            path.addLast(next);
            visited.add(arrival);
            extend(path, visited, destination, results);
            path.removeLast();
            visited.remove(arrival);
        }
    }

    /** Assembles an {@link Itinerary} from an ordered path of flights. */
    private Itinerary toItinerary(Deque<Flight> path) {
        List<Flight> segments = new ArrayList<>(path);
        List<Layover> layovers = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (int i = 0; i < segments.size(); i++) {
            totalPrice = totalPrice.add(segments.get(i).price());
            if (i > 0) {
                layovers.add(rules.toLayover(segments.get(i - 1), segments.get(i)));
            }
        }

        Duration totalDuration = Duration.between(
                segments.getFirst().departureTime(),
                segments.getLast().arrivalTime());

        return new Itinerary(segments, layovers, totalDuration, totalPrice);
    }
}
