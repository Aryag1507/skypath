package com.skypath.data.dto;

/**
 * Raw airport entry as it appears in {@code flights.json}. Kept separate from
 * the {@link com.skypath.domain.Airport} domain type so the on-disk JSON shape
 * can evolve independently of the model; the loader maps between the two.
 */
public record AirportDto(
        String code,
        String name,
        String city,
        String country,
        String timezone
) {
}
