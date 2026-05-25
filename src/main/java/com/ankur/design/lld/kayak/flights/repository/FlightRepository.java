package com.ankur.design.lld.kayak.flights.repository;

import com.ankur.design.lld.kayak.flights.model.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * JpaSpecificationExecutor unlocks findAll(Specification, Sort) —
 * lets us build dynamic WHERE clauses without writing JPQL strings.
 */
public interface FlightRepository
        extends JpaRepository<Flight, Long>,
                JpaSpecificationExecutor<Flight> {
}