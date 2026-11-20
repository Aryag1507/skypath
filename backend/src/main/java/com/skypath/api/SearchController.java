package com.skypath.api;

import com.skypath.api.dto.SearchResponse;
import com.skypath.data.FlightRepository;
import com.skypath.domain.Itinerary;
import com.skypath.search.ItinerarySearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * REST entry point for itinerary search.
 *
 * <p>{@code GET /api/search?origin=JFK&destination=LAX&date=2024-03-15}
 *
 * <p>Validates and normalizes the request before delegating to the search
 * service; invalid input results in HTTP 400 (see {@link ApiExceptionHandler}),
 * while a valid request that simply has no routes returns HTTP 200 with an empty
 * list.
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private static final Pattern IATA_CODE = Pattern.compile("^[A-Z]{3}$");

    private final ItinerarySearchService searchService;
    private final FlightRepository flights;
    private final ItineraryMapper mapper;

    public SearchController(ItinerarySearchService searchService,
                            FlightRepository flights,
                            ItineraryMapper mapper) {
        this.searchService = searchService;
        this.flights = flights;
        this.mapper = mapper;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam String date) {

        String originCode = normalizeCode(origin, "origin");
        String destinationCode = normalizeCode(destination, "destination");

        if (originCode.equals(destinationCode)) {
            throw new InvalidRequestException(
                    "origin and destination must be different airports");
        }
        requireKnownAirport(originCode, "origin");
        requireKnownAirport(destinationCode, "destination");

        LocalDate parsedDate = parseDate(date);

        List<Itinerary> itineraries =
                searchService.search(originCode, destinationCode, parsedDate);
        return mapper.toSearchResponse(
                originCode, destinationCode, parsedDate.toString(), itineraries);
    }

    private String normalizeCode(String raw, String field) {
        String code = raw.trim().toUpperCase();
        if (!IATA_CODE.matcher(code).matches()) {
            throw new InvalidRequestException(
                    field + " must be a 3-letter IATA airport code");
        }
        return code;
    }

    private void requireKnownAirport(String code, String field) {
        if (!flights.hasAirport(code)) {
            throw new InvalidRequestException(
                    "Unknown " + field + " airport: " + code);
        }
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(
                    "date must be in ISO format YYYY-MM-DD");
        }
    }
}
