package com.skypath.data;

import com.skypath.domain.Airport;

import java.util.Optional;

/**
 * Read-only lookup of airports by IATA code.
 *
 * <p>Introduced so that collaborators which only need to resolve codes to
 * airports (e.g. the connection rules) depend on this narrow abstraction rather
 * than the full {@link FlightRepository}. This keeps those components easy to
 * unit-test with a trivial stub.
 */
public interface AirportCatalog {

    Optional<Airport> findAirport(String code);

    boolean hasAirport(String code);
}
