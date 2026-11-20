package com.skypath.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A complete itinerary as presented to the client: its legs, the layovers
 * between them, and the aggregate totals used for display and comparison.
 * Durations are given in minutes plus a formatted label.
 */
public record ItineraryResponse(
        int stops,
        long totalDurationMinutes,
        String totalDurationLabel,
        BigDecimal totalPrice,
        List<SegmentResponse> segments,
        List<LayoverResponse> layovers
) {
}
