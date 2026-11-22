package com.skypath.data;

import com.skypath.domain.Airport;
import com.skypath.domain.Flight;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void emptySourceFailsWithAClearMessage() {
        // e.g. a bind-mount that didn't resolve, leaving an empty file.
        InputStream empty = new ByteArrayInputStream(new byte[0]);

        assertThatThrownBy(() -> loader.parse(empty))
                .hasMessageContaining("empty");
    }

    @Test
    void sourceMissingAirportsAndFlightsFailsWithAClearMessage() {
        InputStream emptyJson = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> loader.parse(emptyJson))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("airports");
    }

    @Test
    void skipsFlightWhoseArrivalIsNotAfterDeparture() {
        // GOOD lands 6h after departure; BAD "arrives" before it leaves once zones
        // are applied (LAX 20:00 PDT = 03:00Z, JFK 22:00 EDT = 02:00Z).
        String json = """
                {
                  "airports": [
                    {"code":"JFK","name":"JFK","city":"NY","country":"US","timezone":"America/New_York"},
                    {"code":"LAX","name":"LAX","city":"LA","country":"US","timezone":"America/Los_Angeles"}
                  ],
                  "flights": [
                    {"flightNumber":"GOOD","airline":"SP","origin":"JFK","destination":"LAX","departureTime":"2024-03-15T08:00:00","arrivalTime":"2024-03-15T11:00:00","price":100,"aircraft":"A320"},
                    {"flightNumber":"BAD","airline":"SP","origin":"LAX","destination":"JFK","departureTime":"2024-03-15T20:00:00","arrivalTime":"2024-03-15T22:00:00","price":100,"aircraft":"A320"}
                  ]
                }
                """;
        List<Flight> flights =
                loader.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))).flights();

        assertThat(flights).extracting(Flight::flightNumber).containsExactly("GOOD");
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
