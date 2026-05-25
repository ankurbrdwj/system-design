package com.ankur.design.lld.kayak.flights.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "flights")
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String airline;
    private String origin;
    private String destination;

    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;

    private double price;
    private double rating;         // airline rating 1.0 – 5.0

    public Flight() {}

    public Flight(String airline, String origin, String destination,
                  LocalDateTime departureTime, LocalDateTime arrivalTime,
                  double price, double rating) {
        this.airline = airline;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.price = price;
        this.rating = rating;
    }

    public Long getId()                     { return id; }
    public String getAirline()              { return airline; }
    public String getOrigin()               { return origin; }
    public String getDestination()          { return destination; }
    public LocalDateTime getDepartureTime() { return departureTime; }
    public LocalDateTime getArrivalTime()   { return arrivalTime; }
    public double getPrice()                { return price; }
    public double getRating()               { return rating; }

    @Override
    public String toString() {
        return String.format("Flight{%s %s→%s dep=%s price=%.2f rating=%.1f}",
                airline, origin, destination, departureTime, price, rating);
    }
}