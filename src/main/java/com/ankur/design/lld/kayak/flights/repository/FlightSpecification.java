package com.ankur.design.lld.kayak.flights.repository;

import com.ankur.design.lld.kayak.flights.model.Flight;
import com.ankur.design.lld.kayak.flights.model.FlightSearchRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a dynamic WHERE clause from FlightSearchRequest.
 *
 * Each filter is added only when the caller provided a non-null value.
 * All predicates are AND-ed together.
 *
 * Criteria API path:
 *   root        → FROM flights (column access)
 *   criteriaBuilder → comparison helpers (equal, lessThanOrEqual, …)
 *   predicates  → accumulated WHERE conditions
 */
public class FlightSpecification {

    public static Specification<Flight> from(FlightSearchRequest req) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // mandatory filters
            predicates.add(cb.equal(root.get("origin"),      req.origin()));
            predicates.add(cb.equal(root.get("destination"), req.destination()));

            // optional: departure window
            if (req.departureFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("departureTime"), req.departureFrom()));
            }
            if (req.departureTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("departureTime"), req.departureTo()));
            }

            // optional: max price
            if (req.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), req.maxPrice()));
            }

            // optional: min rating
            if (req.minRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), req.minRating()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
