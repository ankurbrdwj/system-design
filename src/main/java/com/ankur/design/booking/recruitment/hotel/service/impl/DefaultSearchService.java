package com.ankur.design.booking.recruitment.hotel.service.impl;

import com.ankur.design.booking.recruitment.hotel.model.Hotel;
import com.ankur.design.booking.recruitment.hotel.repository.HotelRepository;
import com.ankur.design.booking.recruitment.hotel.service.SearchService;
import com.ankur.design.booking.recruitment.hotel.util.DistanceComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefaultSearchService implements SearchService {
  private HotelRepository hotelRepository;

  @Autowired
  DefaultSearchService(HotelRepository hotelRepository) {
    this.hotelRepository = hotelRepository;
  }

  @Override
  public List<Hotel> searchByCity(Long cityId, String sortBy) {
    return hotelRepository.findAll().stream()
      .sorted(new DistanceComparator())
      .limit(3)
      .collect(Collectors.toList());
  }
}
