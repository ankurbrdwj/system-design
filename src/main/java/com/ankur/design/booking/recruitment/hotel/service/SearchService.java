package com.ankur.design.booking.recruitment.hotel.service;


import com.ankur.design.booking.recruitment.hotel.model.Hotel;

import java.util.List;

public interface SearchService {

  List<Hotel> searchByCity(Long cityId, String sortBy);


}
