package com.ankur.design.lld.kayak.flights.model;

import java.time.LocalDateTime;

/**
 * Search criteria — all fields optional except origin + destination.
 * null = "no filter on this field".
 */
public record FlightSearchRequest(
        String origin,
        String destination,
        LocalDateTime departureFrom,   // earliest departure
        LocalDateTime departureTo,     // latest departure
        Double maxPrice,
        Double minRating
) {}