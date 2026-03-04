package com.ankur.design.lld.kayak.room.reservation;

import com.ankur.design.lld.kayak.room.reservation.model.BookingRequest;
import com.ankur.design.lld.kayak.room.reservation.service.RoomDatabaseAccessService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {

        // Section 1 - Warming up
        System.err.println("Ankur Bhardwaj");
        System.out.println("Work-from-office preference: Hybrid (2-3 days in office, remainder remote).");

        // --- Setup ---
        RoomDatabaseAccessService service = new RoomDatabaseAccessService();
        service.initializeRooms();

        // 10 booking requests across 3 room types
        List<BookingRequest> requests = List.of(
            new BookingRequest("Alice",   "SINGLE"),
            new BookingRequest("Bob",     "DOUBLE"),
            new BookingRequest("Charlie", "SUITE"),
            new BookingRequest("Diana",   "SINGLE"),
            new BookingRequest("Eve",     "DOUBLE"),
            new BookingRequest("Frank",   "SUITE"),
            new BookingRequest("Grace",   "SINGLE"),
            new BookingRequest("Hank",    "DOUBLE"),
            new BookingRequest("Ivy",     "SUITE"),
            new BookingRequest("Jack",    "DOUBLE")
        );

        // Process all requests concurrently — simulates multiple users hitting the site at once
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (BookingRequest request : requests) {
            executor.submit(() -> service.bookRoom(request));
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Final room availability summary
        System.out.println("\n--- Final Room Availability ---");
        service.getAllRooms().forEach(System.out::println);
    }
}
