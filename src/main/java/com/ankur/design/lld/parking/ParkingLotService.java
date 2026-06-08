package com.ankur.design.lld.parking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

// implements ParkingService  → callers depend on the interface, not this class
// FeeCalculator injected     → pricing strategy swappable without touching this class
// SpotSelector injected      → selection strategy swappable without touching this class
public class ParkingLotService implements ParkingService {

    private final Semaphore             capacity;
    private final List<ParkingSpot>     spots;
    private final FeeCalculator         feeCalculator;   // HOW to price — injected
    private final SpotSelector          spotSelector;    // HOW to pick a spot — injected
    private final Map<String, Ticket>   ticketIndex  = new ConcurrentHashMap<>();
    private final Map<String, Ticket>   vehicleIndex = new ConcurrentHashMap<>();

    public ParkingLotService(int totalSpots,
                             FeeCalculator feeCalculator,
                             SpotSelector spotSelector) {
        this.capacity      = new Semaphore(totalSpots, true);
        this.feeCalculator = feeCalculator;
        this.spotSelector  = spotSelector;
        this.spots         = new ArrayList<>();
        for (int i = 1; i <= totalSpots; i++) spots.add(new ParkingSpot("S" + i));
    }

    @Override
    public Ticket enter(String vehicleNumber) throws InterruptedException {
        if (vehicleIndex.containsKey(vehicleNumber)) {
            throw new IllegalStateException("Vehicle already parked: " + vehicleNumber);
        }

        capacity.acquire();

        ParkingSpot spot = spotSelector.select(spots).orElse(null);
        if (spot == null) {
            capacity.release();
            throw new IllegalStateException("No free spot found despite permit — bug");
        }

        Ticket ticket = new Ticket(UUID.randomUUID().toString(), vehicleNumber, spot.id);
        ticketIndex.put(ticket.ticketId, ticket);
        vehicleIndex.put(vehicleNumber, ticket);
        return ticket;
    }

    @Override
    public BigDecimal pay(String ticketId) {
        Ticket ticket = getTicket(ticketId);
        ticket.lock.lock();
        try {
            if (ticket.state != TicketState.ACTIVE) {
                throw new IllegalStateException("Ticket not active: " + ticket.state);
            }
            ticket.state = TicketState.PAID;
            return feeCalculator.calculate(ticket);   // delegate to injected strategy
        } finally {
            ticket.lock.unlock();
        }
    }

    @Override
    public void exit(String ticketId) {
        Ticket ticket = getTicket(ticketId);
        ticket.lock.lock();
        try {
            if (ticket.state != TicketState.PAID) {
                throw new IllegalStateException("Must pay before exit. State: " + ticket.state);
            }
            ticket.state = TicketState.CLOSED;
        } finally {
            ticket.lock.unlock();
        }

        findSpot(ticket.spotId).release();
        capacity.release();
        vehicleIndex.remove(ticket.vehicleNumber);
    }

    @Override
    public int availableSpots() { return capacity.availablePermits(); }

    private ParkingSpot findSpot(String spotId) {
        return spots.stream()
                .filter(s -> s.id.equals(spotId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Spot not found: " + spotId));
    }

    private Ticket getTicket(String ticketId) {
        Ticket t = ticketIndex.get(ticketId);
        if (t == null) throw new IllegalArgumentException("Ticket not found: " + ticketId);
        return t;
    }
}