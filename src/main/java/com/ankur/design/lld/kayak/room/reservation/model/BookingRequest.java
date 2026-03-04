package com.ankur.design.lld.kayak.room.reservation.model;

import lombok.Getter;

/**
 * Immutable booking request from a guest.
 */
@Getter
public class BookingRequest {

    private final String guestName;
    private final String roomType;

    public BookingRequest(String guestName, String roomType) {
        this.guestName = guestName;
        this.roomType = roomType;
    }

    @Override
    public String toString() {
        return "BookingRequest{guest='" + guestName + "', roomType='" + roomType + "'}";
    }
}