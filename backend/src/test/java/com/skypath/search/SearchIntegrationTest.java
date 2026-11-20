package com.skypath.search;

import com.skypath.domain.Itinerary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end search tests against the real {@code flights.json}, covering the
 * official scenarios that are about search correctness (the validation-only
 * cases are exercised at the API layer). Verifies that results are always
 * ranked by total travel time and have positive durations &mdash; the latter
 * being the key signal that time zones and date-line crossings are handled.
 */
@SpringBootTest
class SearchIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2024, 3, 15);

    @Autowired
    private ItinerarySearchService service;

    @Test
    void case1_jfkToLax_returnsDirectAndMultiStop() {
        List<Itinerary> results = service.search("JFK", "LAX", DATE);

        assertThat(results).isNotEmpty();
        assertThat(results).anySatisfy(i -> assertThat(i.stops()).isZero());        // a direct
        assertThat(results).anySatisfy(i -> assertThat(i.stops()).isPositive());    // a connection
        assertSortedByDuration(results);
    }

    @Test
    void case2_sfoToNrt_internationalRouteHasResults() {
        List<Itinerary> results = service.search("SFO", "NRT", DATE);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(i -> assertThat(i.totalDuration()).isPositive());
        assertSortedByDuration(results);
    }

    @Test
    void case3_bosToSea_noDirectMustConnect() {
        List<Itinerary> results = service.search("BOS", "SEA", DATE);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(i -> assertThat(i.stops()).isPositive()); // no direct exists
        assertSortedByDuration(results);
    }

    @Test
    void case6_sydToLax_dateLineCrossingHasPositiveDurations() {
        List<Itinerary> results = service.search("SYD", "LAX", DATE);

        assertThat(results).isNotEmpty();
        assertThat(results).anySatisfy(i -> assertThat(i.stops()).isZero()); // direct exists
        // Arrival local time appears "before" departure; correct UTC duration is positive.
        assertThat(results).allSatisfy(i -> assertThat(i.totalDuration()).isPositive());
        assertSortedByDuration(results);
    }

    private void assertSortedByDuration(List<Itinerary> results) {
        for (int i = 1; i < results.size(); i++) {
            Duration prev = results.get(i - 1).totalDuration();
            Duration curr = results.get(i).totalDuration();
            assertThat(prev.compareTo(curr)).isLessThanOrEqualTo(0);
        }
    }
}
