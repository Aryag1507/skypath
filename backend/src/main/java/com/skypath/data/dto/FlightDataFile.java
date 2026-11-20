package com.skypath.data.dto;

import java.util.List;

/**
 * Top-level shape of {@code flights.json}: a list of airports and a list of
 * flights.
 */
public record FlightDataFile(
        List<AirportDto> airports,
        List<FlightDto> flights
) {
}
