package com.skypath.api;

import com.skypath.api.dto.ItineraryResponse;
import com.skypath.api.dto.LayoverResponse;
import com.skypath.api.dto.SearchResponse;
import com.skypath.api.dto.SegmentResponse;
import com.skypath.domain.Flight;
import com.skypath.domain.Itinerary;
import com.skypath.domain.Layover;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Translates domain {@link Itinerary} objects into the client-facing response
 * shape: ISO time strings, durations in minutes plus formatted labels. Keeping
 * this mapping in one place lets the API contract evolve independently of the
 * domain model.
 */
@Component
public class ItineraryMapper {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public SearchResponse toSearchResponse(
            String origin, String destination, String date, List<Itinerary> itineraries) {
        List<ItineraryResponse> mapped = itineraries.stream()
                .map(this::toItineraryResponse)
                .toList();
        return new SearchResponse(origin, destination, date, mapped.size(), mapped);
    }

    private ItineraryResponse toItineraryResponse(Itinerary itinerary) {
        return new ItineraryResponse(
                itinerary.stops(),
                itinerary.totalDuration().toMinutes(),
                formatDuration(itinerary.totalDuration()),
                itinerary.totalPrice(),
                itinerary.segments().stream().map(this::toSegmentResponse).toList(),
                itinerary.layovers().stream().map(this::toLayoverResponse).toList());
    }

    private SegmentResponse toSegmentResponse(Flight flight) {
        return new SegmentResponse(
                flight.flightNumber(),
                flight.airline(),
                flight.origin(),
                flight.destination(),
                flight.departureTime().format(ISO),
                flight.arrivalTime().format(ISO),
                flight.duration().toMinutes(),
                flight.price(),
                flight.aircraft());
    }

    private LayoverResponse toLayoverResponse(Layover layover) {
        return new LayoverResponse(
                layover.airportCode(),
                layover.duration().toMinutes(),
                formatDuration(layover.duration()),
                layover.international());
    }

    /** Formats a duration as e.g. "6h 30m", or "45m" when under an hour. */
    static String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long hours = minutes / 60;
        long mins = minutes % 60;
        return hours > 0 ? hours + "h " + mins + "m" : mins + "m";
    }
}
