package com.ankur.design.lld.elevator;

public class Elevator {

    private final int id;
    private int currentFloor;
    private int destination;
    private ElevatorState state;

    public Elevator(int id, int initialFloor) {
        this.id = id;
        this.currentFloor = initialFloor;
        this.destination  = initialFloor;
        this.state        = ElevatorState.IDLE;
    }

    public int getId()             { return id; }
    public int getCurrentFloor()   { return currentFloor; }
    public int getDestination()    { return destination; }
    public ElevatorState getState(){ return state; }

    public void setState(ElevatorState state) {
        this.state = state;
    }

    public void assignDestination(int floor) {
        this.destination = floor;
        this.state = floor > currentFloor ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN;
    }
}
