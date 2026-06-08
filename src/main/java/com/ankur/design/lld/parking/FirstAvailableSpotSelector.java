package com.ankur.design.lld.parking;

import java.util.List;
import java.util.Optional;

public class FirstAvailableSpotSelector implements SpotSelector {

    @Override
    public Optional<ParkingSpot> select(List<ParkingSpot> spots) {
        return spots.stream()
                .filter(s -> s.tryOccupy())   // CAS — only one thread claims each spot
                .findFirst();
    }
}