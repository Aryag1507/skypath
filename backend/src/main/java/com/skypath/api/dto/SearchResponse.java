package com.skypath.api.dto;

import java.util.List;

/**
 * Top-level payload for a search: the echoed query, how many itineraries were
 * found, and the itineraries themselves (sorted shortest travel time first).
 */
public record SearchResponse(
        String origin,
        String destination,
        String date,
        int resultCount,
        List<ItineraryResponse> itineraries
) {
}
