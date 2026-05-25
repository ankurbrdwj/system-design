package com.ankur.design.lld.elevator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD — tests written before implementation.
 * All tests will FAIL until the production classes are created.
 *
 * Building: 10 floors (0-9), 3 elevators, centralized dispatch.
 */
class CentralSystemTest {

    private CentralSystem system;

    @BeforeEach
    void setUp() {
        system = new CentralSystem(3, 10); // 3 elevators, 10 floors
    }

    /**
     * Test 1 — baseline dispatch.
     *
     * All elevators start at floor 0.
     * A hall call arrives for floor 5.
     * The system must assign one elevator and return it (not null).
     */
    @Test
    void dispatchAssignsAnElevatorToRequestedFloor() {
        Elevator assigned = system.dispatch(new FloorRequest(5));

        assertNotNull(assigned, "CentralSystem must assign an elevator, not return null");
        assertEquals(5, assigned.getDestination(), "Assigned elevator destination must be floor 5");
    }

    /**
     * Test 2 — nearest elevator wins.
     *
     * Elevators at floors 0, 3, 8.
     * Request for floor 5.
     * Elevator at floor 3 is nearest (distance 2 vs 5 vs 3) — must be chosen.
     */
    @Test
    void nearestElevatorIsDispatched() {
        Elevator e0 = new Elevator(0, 0);
        Elevator e1 = new Elevator(1, 3);
        Elevator e2 = new Elevator(2, 8);
        CentralSystem customSystem = new CentralSystem(e0, e1, e2);

        Elevator assigned = customSystem.dispatch(new FloorRequest(5));

        assertEquals(e1.getId(), assigned.getId(),
                "Elevator at floor 3 is closest to floor 5 (distance 2)");
    }

    /**
     * Test 3 — idle beats moving at equal distance.
     *
     * Two elevators both at distance 2 from floor 5:
     *   e0 at floor 3 — MOVING_UP (already busy)
     *   e1 at floor 7 — IDLE
     * Idle elevator (e1) must be preferred even though distances are equal.
     *
     * FAILS: ElevatorState does not exist yet, Elevator has no setState().
     */
    @Test
    void idleElevatorIsPreferredOverMovingAtEqualDistance() {
        Elevator e0 = new Elevator(0, 3);
        e0.setState(ElevatorState.MOVING_UP);

        Elevator e1 = new Elevator(1, 7);
        e1.setState(ElevatorState.IDLE);

        CentralSystem customSystem = new CentralSystem(e0, e1);

        Elevator assigned = customSystem.dispatch(new FloorRequest(5));

        assertEquals(e1.getId(), assigned.getId(),
                "IDLE elevator should be preferred over MOVING at equal distance");
    }

    // -------------------------------------------------------------------------
    // DSM proof tests
    // -------------------------------------------------------------------------

    /**
     * DSM property 1 — DETERMINISM.
     * Same elevator positions + same request must always pick the same elevator.
     * Run 10 times to prove there is no randomness in scheduling.
     */
    @RepeatedTest(10)
    void sameInputAlwaysProducesSameOutput() {
        Elevator e0 = new Elevator(0, 0);
        Elevator e1 = new Elevator(1, 4);
        Elevator e2 = new Elevator(2, 9);
        CentralSystem s = new CentralSystem(e0, e1, e2);

        Elevator assigned = s.dispatch(new FloorRequest(5));

        assertEquals(e1.getId(), assigned.getId(),
                "Deterministic scheduler must always pick elevator at floor 4 (distance 1)");
    }

    /**
     * DSM property 2 — STATE TRANSITION ON EVENT.
     * Dispatching a request is an event.
     * Before: elevator is IDLE.
     * After:  elevator must be MOVING (not still IDLE).
     * Proves the DSM transitioned state correctly on the event.
     */
    @Test
    void dispatchEventTransitionsElevatorFromIdleToMoving() {
        Elevator e0 = new Elevator(0, 0);
        CentralSystem s = new CentralSystem(e0);

        assertEquals(ElevatorState.IDLE, e0.getState(), "Before dispatch: must be IDLE");

        s.dispatch(new FloorRequest(5));

        assertNotEquals(ElevatorState.IDLE, e0.getState(), "After dispatch: must not be IDLE");
        assertEquals(ElevatorState.MOVING_UP, e0.getState(), "Moving to floor 5 from 0: must be MOVING_UP");
    }

    /**
     * DSM property 3 — BLOCKED TRANSITION.
     * OUT_OF_SERVICE is a terminal state — no request should ever be routed to it.
     * The DSM must skip invalid (out-of-service) elevators entirely.
     */
    @Test
    void outOfServiceElevatorIsNeverDispatched() {
        Elevator e0 = new Elevator(0, 0);
        e0.setState(ElevatorState.OUT_OF_SERVICE);

        Elevator e1 = new Elevator(1, 9); // farther but valid
        CentralSystem s = new CentralSystem(e0, e1);

        Elevator assigned = s.dispatch(new FloorRequest(1));

        assertEquals(e1.getId(), assigned.getId(),
                "OUT_OF_SERVICE elevator must never be assigned even if it is closer");
    }

    /**
     * DSM property 4 — SYSTEM-LEVEL STATE (FAILS).
     * CentralSystem itself is a DSM with its own state: NORMAL, EMERGENCY.
     * In EMERGENCY mode, no new dispatches should be accepted.
     *
     * FAILS: CentralSystem has no getSystemState() or onEmergency() yet.
     */
    @Test
    void emergencyEventTransitionsSystemToEmergencyAndBlocksDispatches() {
        system.onEmergency();   // fires the emergency event

        assertEquals(SystemState.EMERGENCY, system.getSystemState(),
                "After emergency event, system state must be EMERGENCY");

        Elevator assigned = system.dispatch(new FloorRequest(5));
        assertNull(assigned, "In EMERGENCY state, no new dispatches should be accepted");
    }
}