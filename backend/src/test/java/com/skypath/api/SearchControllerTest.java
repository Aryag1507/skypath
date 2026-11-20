package com.skypath.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@code /api/search} against the real dataset, covering a
 * successful search and the validation cases (same airport, unknown code,
 * malformed date, missing parameter). Together with the search integration
 * tests these exercise the six official scenarios end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void case1_validSearchReturnsSortedResults() throws Exception {
        mvc.perform(get("/api/search")
                        .param("origin", "JFK")
                        .param("destination", "LAX")
                        .param("date", "2024-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("JFK"))
                .andExpect(jsonPath("$.destination").value("LAX"))
                .andExpect(jsonPath("$.resultCount").isNumber())
                .andExpect(jsonPath("$.itineraries[0].segments").isNotEmpty())
                .andExpect(jsonPath("$.itineraries[0].totalDurationMinutes").isNumber());
    }

    @Test
    void acceptsLowercaseCodes() throws Exception {
        mvc.perform(get("/api/search")
                        .param("origin", "jfk")
                        .param("destination", "lax")
                        .param("date", "2024-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("JFK"));
    }

    @Test
    void case4_sameOriginAndDestinationIsBadRequest() throws Exception {
        mvc.perform(get("/api/search")
                        .param("origin", "JFK")
                        .param("destination", "JFK")
                        .param("date", "2024-03-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "origin and destination must be different airports"));
    }

    @Test
    void case5_unknownAirportCodeIsBadRequest() throws Exception {
        mvc.perform(get("/api/search")
                        .param("origin", "XXX")
                        .param("destination", "LAX")
                        .param("date", "2024-03-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unknown origin airport: XXX"));
    }

    @Test
    void malformedCodeIsBadRequest() throws Exception {
        mvc.perform(get("/api/search")
                        .param("origin", "NEWYORK")
                        .param("destination", "LAX")
                        .param("date", "2024-03-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "origin must be a 3-letter IATA airport code"));
    }

    @Test
    void malformedDateIsBadRequest() throws Exception {
        mvc.perform(get("/api/search")
                        .param("origin", "JFK")
                        .param("destination", "LAX")
                        .param("date", "15-03-2024"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "date must be in ISO format YYYY-MM-DD"));
    }

    @Test
    void missingParameterIsBadRequest() throws Exception {
        mvc.perform(get("/api/search")
                        .param("origin", "JFK")
                        .param("date", "2024-03-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Missing required parameter: destination"));
    }
}
