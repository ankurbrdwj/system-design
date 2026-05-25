package com.ankur.design.lld.elevator;

public class FloorRequest {

    private final int targetFloor;

    public FloorRequest(int floor) {
        this.targetFloor = floor;
    }

    public int getTargetFloor() {
        return targetFloor;
    }
}
