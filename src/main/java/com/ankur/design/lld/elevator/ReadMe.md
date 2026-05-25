Design an elevator control system for a building. 
The system should handle multiple elevators, floor requests, 
and move elevators efficiently to service requests.

The system manages 3 elevators serving 10 floors, numbered 0 through 9 (where 0 is the ground floor).
Composition
Design an Elevator Control System
Design an elevator control system for a multi-story building that efficiently manages multiple elevators, processes floor requests from both elevator cars and floor panels, and optimizes passenger wait and travel times through intelligent scheduling.

Asked at:

Google
Google

An elevator control system is the real-time dispatching and orchestration brain for a building’s elevators. Passengers press hall call buttons on floors and select destinations inside cars; the system assigns the best elevator, sequences stops, opens and closes doors, and updates indicators. Think of it like Uber for vertical transport, continuously matching requests to cars under tight safety and latency constraints.

Interviewers ask this to evaluate your ability to model real-time, stateful systems with strict ordering, to design cost-based schedulers, and to reason about reliability and failover. It tests whether you can translate user-centric goals (short waits, smooth rides) into deterministic control loops, data models, and algorithms, especially in a centralized, command-driven architecture like the one described in the real interview context.

Community Solutions
Preview of Design an Elevator Control System
Design an Elevator Control System
by Marcio Rogel
3
11
Preview of Design an Elevator Control System
Design an Elevator Control System
by WillowyAmaranthShrimp324
1
3
Preview of Design an Elevator Control System
Design an Elevator Control System
by Luiz Santana
0
1
Preview of Design an Elevator Control System
Design an Elevator Control System
by OperationalSalmonRoadrunner706
0
3
Common Functional Requirements
Most candidates end up covering this set of core functionalities

Users should be able to request an elevator from any floor (up/down) and be picked up promptly.

Users should be able to select destination floors inside the elevator and have their trip served efficiently.

Users should be able to see clear arrival indicators and door status so they know when to board and exit.

Building operators should be able to set service modes (e.g., out-of-service, maintenance, emergency priority) that safely override normal scheduling.

Common Deep Dives
Common follow-up questions interviewers like to ask for this question









Relevant Patterns
Relevant patterns that you should know for this question

Real-time Updates
The controller must react to button presses and car telemetry in real time and push assignments and indicator updates within tens of milliseconds. Pub/sub of state deltas and timely propagation are essential to keep the system responsive and consistent.

Dealing with Contention
Rush-hour bursts create high contention for the same resources (cars and floors). You need strategies like deduping hall calls, zoning, backpressure, and fairness/aging to prevent thrashing and starvation under load.

Multi-step Processes
Each trip is a multi-step workflow (assign, travel, arrive, door open/close, boarding) with retries and preemption (e.g., emergency). Modeling this as a durable, idempotent workflow prevents partial progress and inconsistent state.

Relevant Technologies
Relevant technologies that could be used to solve this question

Redis
Redis provides a fast, in-memory source of truth for per-building state (car positions, queues) and supports atomic operations and data structures (sorted sets, hashes) for scheduling and deduping, plus lightweight pub/sub for pushing updates to panels and cars.

Kafka
Kafka gives you an append-only event log for button presses, telemetry, and assignments. It enables replay to reconstruct state after failover, backpressure during bursts, and decoupled analytics/simulation without impacting the control loop.

ZooKeeper
ZooKeeper is well-suited for leader election and coordination of the Central System instances per building, storing ephemeral nodes/heartbeats to detect failures quickly and orchestrate deterministic failover.

Similar Problems to Practice
Related problems to practice for this question

Uber
Both systems match supply to demand in real time with a cost function (ETA, direction, load), handle bursts (rush hour), and require consistent, low-latency dispatch decisions and failover strategies.

Job Scheduler
Assigning jobs to workers mirrors assigning floor requests to cars: you must track worker state, ensure idempotent assignment, handle retries/failures, and optimize throughput/latency under constraints.

FB Live Comments
Real-time fan-out of state (arrival indicators, car positions) to many clients is akin to live comments updates, requiring low-latency pub/sub and deduplication of frequent, small updates.

Red Flags to Avoid
Common mistakes that can sink candidates in an interview

Single, non-redundant Central System with no failover or event log

This creates a single point of failure; a controller crash would strand passengers, lose assignments, and cause unsafe or inconsistent behavior when it restarts without a way to reconstruct state.

Relying on eventual consistency for command ordering and state

Out-of-order or duplicated commands can cause oscillations (cars reversing), double-served calls, and safety risks. Control loops need strong ordering, idempotency, and deterministic conflict resolution.

Naive scheduling (first-come-first-served per car) ignoring direction, batching, and fairness

This leads to ping-pong behavior, long average and tail waits during peak traffic, and starvation for distant floors. A proper cost-based, direction-aware scheduler with aging is expected.

Question Timeline
See when this question was last asked and where, including any notes left by other candidates.

All Companies
Company
Level
Level
Region
Mid October, 2025

Google
Google

Manager

This elevator control system is designed for a 10-story building served by four independent elevators, utilizing a centralized, command-driven architecture. All requests, whether from a floor button or inside an elevator car, are sent immediately as a command packet (e.g., [timestamp, control_panel_id, target_floor]) to a Central System. The core function of this Central System is to maintain the real-time state of all four elevators and the global floor requests. Upon receiving a command, it applies a dynamic scheduling algorithm to determine which elevator can most efficiently fulfill the request, aiming to minimize passenger wait and travel times, before dispatching the chosen elevator to its next destination.