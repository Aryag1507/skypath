package com.skypath.search;

import com.skypath.data.FlightData;
import com.skypath.data.FlightRepository;
import com.skypath.domain.Airport;
import com.skypath.domain.Flight;
import com.skypath.domain.Itinerary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the search algorithm over small, hand-built graphs so each
 * behaviour (direct vs connecting routes, ranking, skipping invalid connections, the
 * two-stop cap, no-revisit, and the date filter) is exercised in isolation.
 *
 * <p>All airports here are placed in UTC and the US so durations read directly
 * and connections are domestic; the domestic/international nuances are covered
 * by {@link ConnectionRulesTest}.
 */
class ItinerarySearchServiceTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final LocalDate DATE = LocalDate.of(2024, 3, 15);

    @Test
    void returnsDirectFlight() {
        var service = serviceFor(List.of(
                flight("F1", "A", "B", at(8, 0), at(10, 0))));

        List<Itinerary> results = service.search("A", "B", DATE);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().stops()).isZero();
        assertThat(results.getFirst().totalDuration()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void ranksShorterTotalTravelTimeFirst() {
        var service = serviceFor(List.of(
                flight("DIRECT", "A", "B", at(8, 0), at(10, 0)),   // 2h
                flight("L1", "A", "C", at(8, 0), at(9, 0)),        // 1-stop via C
                flight("L2", "C", "B", at(10, 0), at(12, 0))));    // total 4h

        List<Itinerary> results = service.search("A", "B", DATE);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).stops()).isZero();                       // direct first
        assertThat(results.get(0).totalDuration()).isEqualTo(Duration.ofHours(2));
        assertThat(results.get(1).stops()).isEqualTo(1);
        assertThat(results.get(1).totalDuration()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void skipsConnectionWithTooShortLayover() {
        var service = serviceFor(List.of(
                flight("L1", "A", "C", at(8, 0), at(9, 0)),
                flight("L2", "C", "B", at(9, 20), at(11, 0)))); // only 20 min at C

        assertThat(service.search("A", "B", DATE)).isEmpty();
    }

    @Test
    void skipsConnectionWithTooLongLayover() {
        var service = serviceFor(List.of(
                flight("L1", "A", "C", at(8, 0), at(9, 0)),
                flight("L2", "C", "B", at(16, 0), at(18, 0)))); // 7h at C

        assertThat(service.search("A", "B", DATE)).isEmpty();
    }

    @Test
    void doesNotExceedTwoStops() {
        var service = serviceFor(List.of(
                flight("S1", "A", "C", at(8, 0), at(9, 0)),
                flight("S2", "C", "E", at(10, 0), at(11, 0)),
                flight("S3", "E", "B", at(12, 0), at(13, 0)),   // 2-stop route A-C-E-B
                flight("S4", "E", "G", at(12, 0), at(13, 0)),
                flight("S5", "G", "B", at(14, 0), at(15, 0)))); // would be 3-stop A-C-E-G-B

        List<Itinerary> results = service.search("A", "B", DATE);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().stops()).isEqualTo(2);
        assertThat(results).allSatisfy(i -> assertThat(i.stops()).isLessThanOrEqualTo(2));
    }

    @Test
    void doesNotRevisitAirports() {
        var service = serviceFor(List.of(
                flight("DIRECT", "A", "B", at(8, 0), at(10, 0)),
                flight("OUT", "A", "C", at(8, 0), at(9, 0)),
                flight("BACK", "C", "A", at(10, 0), at(11, 0)))); // loops back to A

        List<Itinerary> results = service.search("A", "B", DATE);

        assertThat(results).hasSize(1); // only the direct; no cycle through A
        assertThat(results.getFirst().stops()).isZero();
        results.forEach(i -> {
            Set<String> airports = new HashSet<>();
            airports.add(i.segments().getFirst().origin());
            i.segments().forEach(s -> airports.add(s.destination()));
            assertThat(airports).doesNotHaveDuplicates();
        });
    }

    @Test
    void firstLegMustDepartOnRequestedDate() {
        ZonedDateTime nextDay = ZonedDateTime.of(2024, 3, 16, 8, 0, 0, 0, UTC);
        var service = serviceFor(List.of(
                new Flight("F1", "SP", "A", "B", nextDay, nextDay.plusHours(2),
                        BigDecimal.valueOf(100), "A320")));

        assertThat(service.search("A", "B", DATE)).isEmpty(); // departs 03-16, not 03-15
    }

    @Test
    void sameOriginAndDestinationReturnsEmpty() {
        var service = serviceFor(List.of(
                flight("F1", "A", "B", at(8, 0), at(10, 0))));

        assertThat(service.search("A", "A", DATE)).isEmpty();
    }

    @Test
    void itineraryIncludesLayoverDetails() {
        var service = serviceFor(List.of(
                flight("L1", "A", "C", at(8, 0), at(9, 0)),
                flight("L2", "C", "B", at(10, 0), at(12, 0)))); // 1h layover at C

        Itinerary itinerary = service.search("A", "B", DATE).getFirst();

        assertThat(itinerary.layovers()).hasSize(1);
        assertThat(itinerary.layovers().getFirst().airportCode()).isEqualTo("C");
        assertThat(itinerary.layovers().getFirst().duration()).isEqualTo(Duration.ofHours(1));
        assertThat(itinerary.layovers().getFirst().international()).isFalse();
        assertThat(itinerary.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    // --- helpers ---

    private ZonedDateTime at(int hour, int minute) {
        return ZonedDateTime.of(2024, 3, 15, hour, minute, 0, 0, UTC);
    }

    private Flight flight(String number, String origin, String dest,
                          ZonedDateTime departure, ZonedDateTime arrival) {
        return new Flight(number, "SkyPath", origin, dest,
                departure, arrival, BigDecimal.valueOf(100), "A320");
    }

    /** Builds a service over a graph whose airports are inferred from the flights. */
    private ItinerarySearchService serviceFor(List<Flight> flights) {
        Set<String> codes = new HashSet<>();
        for (Flight f : flights) {
            codes.add(f.origin());
            codes.add(f.destination());
        }
        List<Airport> airports = codes.stream()
                .map(c -> new Airport(c, c, c, "US", UTC))
                .toList();
        FlightRepository repo = FlightRepository.withData(new FlightData(airports, flights));
        return new ItinerarySearchService(repo, new ConnectionRules(repo));
    }
}
