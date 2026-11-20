package com.skypath.data;

import com.skypath.domain.Airport;
import com.skypath.domain.Flight;

import java.util.List;

/**
 * The parsed, timezone-resolved dataset produced by {@link FlightDataLoader}:
 * domain airports and flights, ready to be indexed by {@link FlightRepository}.
 */
public record FlightData(
        List<Airport> airports,
        List<Flight> flights
) {
}
