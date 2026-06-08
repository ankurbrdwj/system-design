package com.ankur.design.lld.parking;

enum TicketState {
    ACTIVE,   // car is parked
    PAID,     // fee settled, car may exit
    CLOSED    // car exited
}