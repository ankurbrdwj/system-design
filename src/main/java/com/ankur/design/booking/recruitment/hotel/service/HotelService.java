package com.ankur.design.booking.recruitment.hotel.service;


import com.ankur.design.booking.recruitment.hotel.model.Hotel;

import java.util.List;

public interface HotelService {
  List<Hotel> getAllHotels();

  List<Hotel> getHotelsByCity(Long cityId);

  Hotel createNewHotel(Hotel hotel);

    Hotel getHotelById(Long hotelId);

  void deleteHotelById(Long id);
}
