package com.skypath.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skypath.data.dto.AirportDto;
import com.skypath.data.dto.FlightDataFile;
import com.skypath.data.dto.FlightDto;
import com.skypath.domain.Airport;
import com.skypath.domain.Flight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code flights.json} and converts it into timezone-resolved domain
 * objects.
 *
 * <p>The central responsibility here is the single point at which naive local
 * wall-clock times become absolute instants: a flight's departure is resolved
 * against its <em>origin</em> airport's zone and its arrival against its
 * <em>destination</em> airport's zone. Doing this once, at load time, means no
 * other layer ever has to reason about time zones.
 *
 * <p>Malformed entries (unknown airport codes, unparseable times or zones) are
 * logged and skipped rather than aborting startup, so a single bad record in
 * the dataset cannot take the whole service down.
 */
@Component
public class FlightDataLoader {

    private static final Logger log = LoggerFactory.getLogger(FlightDataLoader.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResourceLoader resourceLoader;
    private final String location;

    public FlightDataLoader(
            ResourceLoader resourceLoader,
            @org.springframework.beans.factory.annotation.Value("${skypath.data.location}") String location) {
        this.resourceLoader = resourceLoader;
        this.location = location;
    }

    /** Loads the dataset from the configured location (file: or classpath:). */
    public FlightData load() {
        Resource resource = resourceLoader.getResource(location);
        log.info("Loading flight data from {}", resource);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Flight data not found at '" + location + "'. Set SKYPATH_DATA_LOCATION "
                            + "or ensure flights.json is present and mounted into the container.");
        }
        try (InputStream in = resource.getInputStream()) {
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Unable to read flight data from " + location, e);
        }
    }

    /**
     * Parses and converts a dataset from a raw JSON stream. Exposed separately
     * from {@link #load()} so it can be exercised directly in tests.
     */
    public FlightData parse(InputStream jsonStream) {
        FlightDataFile file;
        try {
            file = objectMapper.readValue(jsonStream, FlightDataFile.class);
        } catch (IOException e) {
            // An empty source (e.g. a mount that didn't resolve) lands here too.
            throw new UncheckedIOException(
                    "Flight data source is empty or not valid JSON", e);
        }
        if (file == null || (file.airports() == null && file.flights() == null)) {
            throw new IllegalStateException(
                    "Flight data source is empty or missing its 'airports' and 'flights' fields");
        }

        List<Airport> airports = mapAirports(file.airports());
        Map<String, ZoneId> zonesByCode = new HashMap<>();
        for (Airport a : airports) {
            zonesByCode.put(a.code(), a.timezone());
        }

        List<Flight> flights = mapFlights(file.flights(), zonesByCode);
        log.info("Loaded {} airports and {} flights", airports.size(), flights.size());
        return new FlightData(airports, flights);
    }

    private List<Airport> mapAirports(List<AirportDto> dtos) {
        List<Airport> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        for (AirportDto dto : dtos) {
            try {
                ZoneId zone = ZoneId.of(dto.timezone());
                result.add(new Airport(
                        dto.code(), dto.name(), dto.city(), dto.country(), zone));
            } catch (RuntimeException e) {
                log.warn("Skipping airport {} with invalid timezone '{}': {}",
                        dto.code(), dto.timezone(), e.getMessage());
            }
        }
        return result;
    }

    private List<Flight> mapFlights(List<FlightDto> dtos, Map<String, ZoneId> zonesByCode) {
        List<Flight> result = new ArrayList<>();
        if (dtos == null) {
            return result;
        }
        int skipped = 0;
        for (FlightDto dto : dtos) {
            ZoneId originZone = zonesByCode.get(dto.origin());
            ZoneId destZone = zonesByCode.get(dto.destination());
            if (originZone == null || destZone == null) {
                log.warn("Skipping flight {} ({} -> {}): unknown airport code",
                        dto.flightNumber(), dto.origin(), dto.destination());
                skipped++;
                continue;
            }
            try {
                ZonedDateTime departure =
                        LocalDateTime.parse(dto.departureTime()).atZone(originZone);
                ZonedDateTime arrival =
                        LocalDateTime.parse(dto.arrivalTime()).atZone(destZone);
                if (!arrival.isAfter(departure)) {
                    // Guards against bad data: a flight must land after it departs
                    // (compared as absolute instants, so time zones are accounted for).
                    log.warn("Skipping flight {}: arrival is not after departure",
                            dto.flightNumber());
                    skipped++;
                    continue;
                }
                result.add(new Flight(
                        dto.flightNumber(), dto.airline(), dto.origin(), dto.destination(),
                        departure, arrival, dto.price(), dto.aircraft()));
            } catch (DateTimeParseException e) {
                log.warn("Skipping flight {}: unparseable time ({})",
                        dto.flightNumber(), e.getMessage());
                skipped++;
            }
        }
        if (skipped > 0) {
            log.warn("Skipped {} malformed/unresolvable flight records", skipped);
        }
        return result;
    }
}
