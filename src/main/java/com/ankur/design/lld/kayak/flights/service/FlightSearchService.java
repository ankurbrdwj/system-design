package com.ankur.design.lld.kayak.flights.service;

import com.ankur.design.lld.kayak.flights.model.Flight;
import com.ankur.design.lld.kayak.flights.model.FlightSearchRequest;
import com.ankur.design.lld.kayak.flights.repository.FlightRepository;
import com.ankur.design.lld.kayak.flights.repository.FlightSpecification;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlightSearchService {

    private final FlightRepository repository;

    public FlightSearchService(FlightRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns matching flights sorted by price ASC (price elasticity).
     *
     * Hibernate translates this to:
     *   SELECT * FROM flights
     *   WHERE origin = ?
     *     AND destination = ?
     *     AND departureTime >= ?          -- if provided
     *     AND departureTime <= ?          -- if provided
     *     AND price <= ?                  -- if provided
     *     AND rating >= ?                 -- if provided
     *   ORDER BY price ASC
     */
    public List<Flight> search(FlightSearchRequest request) {
        return repository.findAll(
                FlightSpecification.from(request),
                Sort.by(Sort.Direction.ASC, "price")
        );
    }
}