package com.ankur.design.lld.elevator;

import java.util.ArrayList;
import java.util.List;

public class CentralSystem {

    private final List<Elevator> elevators = new ArrayList<>();
    private SystemState systemState = SystemState.NORMAL;

    public SystemState getSystemState() { return systemState; }

    public void onEmergency() {
        systemState = SystemState.EMERGENCY;
        elevators.forEach(e -> e.setState(ElevatorState.OUT_OF_SERVICE));
    }

    public CentralSystem(int numElevators, int floors) {
        for (int i = 0; i < numElevators; i++) {
            elevators.add(new Elevator(i, 0));
        }
    }

    public CentralSystem(Elevator... elevators) {
        for (Elevator e : elevators) {
            this.elevators.add(e);
        }
    }

    public Elevator dispatch(FloorRequest request) {
        if (request == null) return null;
        if (systemState == SystemState.EMERGENCY) return null;

        Elevator best = null;
        int minCost = Integer.MAX_VALUE;

        for (Elevator e : elevators) {
            if (e.getState() == ElevatorState.OUT_OF_SERVICE) continue;

            int distance = Math.abs(e.getCurrentFloor() - request.getTargetFloor());
            // IDLE elevators get no penalty; moving elevators cost more
            int penalty  = e.getState() == ElevatorState.IDLE ? 0 : 10;
            int cost     = distance + penalty;

            if (cost < minCost) {
                minCost = cost;
                best = e;
            }
        }

        if (best != null) {
            best.assignDestination(request.getTargetFloor());
        }
        return best;
    }
}
