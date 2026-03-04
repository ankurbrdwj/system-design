# Kayak Coding Challenge - Room Booking

In this exercise, you'll be presented the code that tries to book total of 10 rooms from different guests. The goal is to make the code work as intended. Also suggest areas of improvement and show us the improvements in the code.

## Notes:

* This is designed to be completed in 2 hours. Our suggestion is not to spend too much time on this challenge.
* It is an open book exercise. You can use any tools and websites the way you would use in a real work environment.
* The code has to be compiled and run for each section, so that we can evaluate the output.
* If there is any part that is not clear to you, make a reasonable assumption, and state it in comment. Adjust code accordingly.
* The code has to run and finish gracefully no matter what.
* Make the output meaningful, and formatted in a way that is easy to read.

## Pre-requisites
- Java 21
- Maven 3.8 or higher
- IntelliJ IDEA or any other IDE of your choice

## Code Concept

- Main - the application's main class.
- Room - represents a hotel room type and its availability
- RoomDatabaseAccessService - provides db access to room data
- BookingRequest - contains a request to book a room

When the application starts, it will initialize rooms and their availability. It will then create a list of booking requests and try to book the rooms. The goal is to process multiple requests simultaneously, while ensuring that the rooms are booked correctly.

In this challenge, just do not make it "work", meaning it outputs something appears to be working. Instead, imagine that you are building a real website, where you have tens of thousands of users are trying to book rooms, while the system needs to maintain integrity. Demonstrate your ability to write code that would work in such a large, real-world system.

## Section 1 - Warming up

Make the app output your name to stderr and the answer to the work-from-office question to stdout. Read the code comment for details, and follow the instructions.

## Section 2 - Workable Code
        
Read the code, and based on the concept above, test and see if it works as intended. State what is the problem in std output, and fix it.

## Section 3 - Optimize the code

Given that code, and thinking in that this is a real website, what would you do to make it more performant?

## Section 4 - Find issues
There may be cases where the code works on the surface, but it is not correct and cause issues when handling with more data, more threads, more requests, etc. Find such potential issues and fix them.

## Section 5 - Bonus Points
### Bonus point 1
Find classes that can be immutable. Discuss if that's a good idea or not, and if so, implement it. If not, explain why.

### Bonus point 2
Write a thorough unit test to test for the RoomDatabaseAccessService class.



## Sections & Implementation Status

1. **Section 1 – Warming up**  
   Printed candidate name to stderr and preferred office to stdout.
2. **Section 2 – Workable Code**  
   Fixed the compile‐error and race conditions by using a `BlockingQueue`, correct JSON loading, and per‐room synchronization.
3. **Section 3 – Optimize the Code**  
   Introduced a bounded `ThreadPoolExecutor`, streaming JSON parsing, and an in‐memory `RoomCatalog` cache for high throughput.
4. **Section 4 – Find Issues**  
   Replaced print statements with SLF4J + Logback, added Micrometer metrics, custom rejection handler, JSON error skipping, MDC context, and a graceful shutdown hook.
5. **Section 5 – Bonus Points**
    - Made `BookingRequest` and `RoomType` immutable.
    - Wrote thorough JUnit 5 tests for `RoomDatabaseAccessService`.

All sections have been fully implemented as described above.

---

## Build & Run

1. **Compile & run the application**  
   mvn clean compile exec:java

text
2. **Run the unit tests**  
   mvn test


