package com.skypath.domain;

import java.time.ZoneId;

/**
 * An airport in the flight network.
 *
 * <p>The {@code timezone} is stored as a {@link ZoneId} rather than a raw
 * string so that every time calculation in the system is timezone-aware by
 * construction. The {@code country} field drives the domestic/international
 * distinction used by the connection rules.
 *
 * @param code     3-letter IATA code, e.g. {@code JFK}
 * @param name     full airport name
 * @param city     city served
 * @param country  ISO country code, e.g. {@code US}
 * @param timezone the airport's local time zone
 */
public record Airport(
        String code,
        String name,
        String city,
        String country,
        ZoneId timezone
) {
}
