package com.ankur.design.lld.parking;

import java.util.List;
import java.util.Optional;

// WHAT spot selection does — swap first-available/nearest-exit/VIP without touching service
public interface SpotSelector {
    Optional<ParkingSpot> select(List<ParkingSpot> spots);
}