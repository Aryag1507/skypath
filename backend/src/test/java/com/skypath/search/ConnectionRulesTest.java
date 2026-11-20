package com.skypath.search;

import com.skypath.data.AirportCatalog;
import com.skypath.domain.Airport;
import com.skypath.domain.Flight;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive, boundary-focused tests for the connection rules. These cover the
 * correctness-critical thresholds that are easy to get wrong (inclusive 45/90
 * minute minimums and the 6-hour maximum) and the domestic/international
 * classification.
 */
class ConnectionRulesTest {

    // Minimal in-memory catalog mapping airport codes to countries.
    private static final Map<String, String> COUNTRIES = Map.of(
            "JFK", "US", "ORD", "US", "LAX", "US",
            "LHR", "GB", "CDG", "FR");

    private final AirportCatalog catalog = new AirportCatalog() {
        @Override
        public Optional<Airport> findAirport(String code) {
            String country = COUNTRIES.get(code);
            return country == null ? Optional.empty()
                    : Optional.of(new Airport(code, code, code, country, ZoneId.of("UTC")));
        }

        @Override
        public boolean hasAirport(String code) {
            return COUNTRIES.containsKey(code);
        }
    };

    private final ConnectionRules rules = new ConnectionRules(catalog);

    private static final ZonedDateTime BASE =
            ZonedDateTime.of(2024, 3, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    /** Arriving flight into {@code via}, landing at BASE. */
    private Flight arrival(String origin, String via) {
        return new Flight("IN", "SkyPath", origin, via,
                BASE.minusHours(3), BASE, BigDecimal.ZERO, "A320");
    }

    /** Departing flight from {@code via}, leaving {@code layover} after BASE. */
    private Flight departure(String via, String dest, Duration layover) {
        ZonedDateTime dep = BASE.plus(layover);
        return new Flight("OUT", "SkyPath", via, dest,
                dep, dep.plusHours(3), BigDecimal.ZERO, "A320");
    }

    // --- Domestic minimum (45 min, inclusive) ---

    @Test
    void domesticLayoverJustUnderMinimumIsInvalid() {
        assertThat(rules.isValidConnection(
                arrival("JFK", "ORD"), departure("ORD", "LAX", Duration.ofMinutes(44))))
                .isFalse();
    }

    @Test
    void domesticLayoverExactlyMinimumIsValid() {
        assertThat(rules.isValidConnection(
                arrival("JFK", "ORD"), departure("ORD", "LAX", Duration.ofMinutes(45))))
                .isTrue();
    }

    // --- International minimum (90 min, inclusive) ---

    @Test
    void internationalLayoverJustUnderMinimumIsInvalid() {
        // JFK(US) -> LHR(GB) -> CDG(FR): international connection, 90 min floor.
        assertThat(rules.isValidConnection(
                arrival("JFK", "LHR"), departure("LHR", "CDG", Duration.ofMinutes(89))))
                .isFalse();
    }

    @Test
    void internationalLayoverExactlyMinimumIsValid() {
        assertThat(rules.isValidConnection(
                arrival("JFK", "LHR"), departure("LHR", "CDG", Duration.ofMinutes(90))))
                .isTrue();
    }

    @Test
    void domesticMinimumIsNotEnoughForAnInternationalConnection() {
        // 60 min clears the domestic floor but not the international one.
        assertThat(rules.isValidConnection(
                arrival("JFK", "LHR"), departure("LHR", "CDG", Duration.ofMinutes(60))))
                .isFalse();
    }

    // --- Maximum (6 hours, inclusive) ---

    @Test
    void layoverExactlyMaximumIsValid() {
        assertThat(rules.isValidConnection(
                arrival("JFK", "ORD"), departure("ORD", "LAX", Duration.ofHours(6))))
                .isTrue();
    }

    @Test
    void layoverJustOverMaximumIsInvalid() {
        assertThat(rules.isValidConnection(
                arrival("JFK", "ORD"), departure("ORD", "LAX", Duration.ofMinutes(361))))
                .isFalse();
    }

    // --- Structural rules ---

    @Test
    void airportChangeDuringLayoverIsInvalid() {
        // Arrive at ORD but the next flight departs from LAX -> not a connection.
        assertThat(rules.isValidConnection(
                arrival("JFK", "ORD"), departure("LAX", "SFO", Duration.ofHours(2))))
                .isFalse();
    }

    @Test
    void onwardFlightDepartingBeforeArrivalIsInvalid() {
        assertThat(rules.isValidConnection(
                arrival("JFK", "ORD"), departure("ORD", "LAX", Duration.ofMinutes(-30))))
                .isFalse();
    }

    // --- Classification ---

    @Test
    void allSameCountryIsDomestic() {
        assertThat(rules.isDomesticConnection(arrival("JFK", "ORD"), departure("ORD", "LAX", Duration.ofHours(1))))
                .isTrue();
    }

    @Test
    void differingCountriesAreInternational() {
        assertThat(rules.isDomesticConnection(arrival("JFK", "LHR"), departure("LHR", "CDG", Duration.ofHours(2))))
                .isFalse();
    }

    @Test
    void toLayoverCapturesAirportDurationAndClassification() {
        var layover = rules.toLayover(
                arrival("JFK", "LHR"), departure("LHR", "CDG", Duration.ofMinutes(120)));

        assertThat(layover.airportCode()).isEqualTo("LHR");
        assertThat(layover.duration()).isEqualTo(Duration.ofMinutes(120));
        assertThat(layover.international()).isTrue();
    }
}
