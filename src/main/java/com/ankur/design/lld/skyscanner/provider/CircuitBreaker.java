package com.ankur.design.lld.skyscanner.provider;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple 3-state circuit breaker: CLOSED → OPEN → HALF_OPEN → CLOSED
 *
 * CLOSED   : requests flow through normally
 * OPEN     : requests fail-fast (provider is down)
 * HALF_OPEN: one probe request allowed to test recovery
 *
 * Thread-safe via atomics — no locks on the hot path.
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String providerId;
    private final int failureThreshold;    // failures before opening
    private final long resetWindowMs;      // how long to stay OPEN

    private final AtomicInteger  failureCount = new AtomicInteger(0);
    private final AtomicLong     openedAt     = new AtomicLong(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    public CircuitBreaker(String providerId, int failureThreshold, long resetWindowMs) {
        this.providerId       = providerId;
        this.failureThreshold = failureThreshold;
        this.resetWindowMs    = resetWindowMs;
    }

    /** Returns true if the request should be allowed through. */
    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) return true;
        if (current == State.OPEN) {
            if (System.currentTimeMillis() - openedAt.get() > resetWindowMs) {
                // try to transition to HALF_OPEN — only one thread wins the CAS
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    System.out.println("[CB] " + providerId + " → HALF_OPEN (probe allowed)");
                }
                return state.get() == State.HALF_OPEN;
            }
            return false;  // still OPEN, fail-fast
        }
        return true; // HALF_OPEN: allow the probe
    }

    public void recordSuccess() {
        if (state.get() != State.CLOSED) {
            System.out.println("[CB] " + providerId + " → CLOSED (recovered)");
            failureCount.set(0);
            state.set(State.CLOSED);
        } else {
            failureCount.set(0);
        }
    }

    public void recordFailure() {
        int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
            openedAt.set(System.currentTimeMillis());
            System.out.println("[CB] " + providerId + " → OPEN after " + failures + " failures");
        } else if (state.get() == State.HALF_OPEN) {
            // probe failed — reopen
            state.set(State.OPEN);
            openedAt.set(System.currentTimeMillis());
            System.out.println("[CB] " + providerId + " probe failed → OPEN again");
        }
    }

    public State getState() { return state.get(); }
}