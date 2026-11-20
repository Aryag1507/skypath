package com.skypath.data;

import com.skypath.domain.Airport;
import com.skypath.domain.Flight;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the loader's core responsibility: parsing the dataset and resolving
 * local wall-clock times against the correct (origin vs destination) zones.
 */
class FlightDataLoaderTest {

    private final FlightDataLoader loader = new FlightDataLoader(null, null);

    private FlightData load() {
        try (InputStream in = getClass().getResourceAsStream("/test-flights.json")) {
            return loader.parse(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void parsesAirportsWithZoneIds() {
        List<Airport> airports = load().airports();

        assertThat(airports).hasSize(3);
        assertThat(airports)
                .filteredOn(a -> a.code().equals("JFK"))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.country()).isEqualTo("US");
                    assertThat(a.timezone()).isEqualTo(ZoneId.of("America/New_York"));
                });
    }

    @Test
    void skipsFlightsReferencingUnknownAirports() {
        // SP3 departs from XXX which is not in the airports list -> dropped.
        List<Flight> flights = load().flights();

        assertThat(flights).extracting(Flight::flightNumber)
                .containsExactlyInAnyOrder("SP1", "SP2");
    }

    @Test
    void resolvesDepartureAgainstOriginZoneAndArrivalAgainstDestinationZone() {
        Flight sp1 = flightByNumber("SP1");

        // 08:00 in New York (EDT, UTC-4 on 2024-03-15) == 12:00Z
        assertThat(sp1.departureTime().toInstant())
                .isEqualTo(java.time.OffsetDateTime.parse("2024-03-15T12:00:00Z").toInstant());
        // 11:00 in Los Angeles (PDT, UTC-7) == 18:00Z
        assertThat(sp1.arrivalTime().toInstant())
                .isEqualTo(java.time.OffsetDateTime.parse("2024-03-15T18:00:00Z").toInstant());
        // Naive same-zone math would give 3h; correct cross-zone duration is 6h.
        assertThat(sp1.duration()).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void handlesOvernightInternationalFlightDuration() {
        Flight sp2 = flightByNumber("SP2");

        // 20:00 NY (UTC-4) = 00:00Z (Mar 16); 07:00 London (GMT, UTC+0) = 07:00Z (Mar 16) => 7h.
        assertThat(sp2.departureTime().withZoneSameInstant(ZoneOffset.UTC).toString())
                .isEqualTo("2024-03-16T00:00Z");
        assertThat(sp2.duration()).isEqualTo(Duration.ofHours(7));
    }

    private Flight flightByNumber(String number) {
        return load().flights().stream()
                .filter(f -> f.flightNumber().equals(number))
                .findFirst()
                .orElseThrow();
    }
}
