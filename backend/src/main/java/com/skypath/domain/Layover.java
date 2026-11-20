package com.skypath.domain;

import java.time.Duration;

/**
 * The connection gap at a stopover airport, between an arriving flight and the
 * next departing flight.
 *
 * <p>{@code international} is pre-computed when the itinerary is assembled (it
 * depends on the countries of the connecting flights) and determines which
 * minimum-layover threshold applied: 90 minutes when international, 45 when
 * domestic.
 *
 * @param airportCode   the airport where the connection happens
 * @param duration      time between arrival and the next departure
 * @param international  whether this connection is international
 */
public record Layover(
        String airportCode,
        Duration duration,
        boolean international
) {
}
