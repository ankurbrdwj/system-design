package com.ankur.design.booking.recruitment.hotel.util;


import com.ankur.design.booking.recruitment.hotel.model.Hotel;

import java.util.Comparator;

public class DistanceComparator implements Comparator<Object>{

  @Override
  public int compare(Object o1, Object o2) {
    Hotel hotel1 = (Hotel) o1;
    Hotel hotel2 = (Hotel) o2;

    double h1Distance = HaversineDistance.haversine(hotel1.getLatitude(), hotel1.getLongitude(),
      hotel1.getCity().getCityCentreLatitude(), hotel1.getCity().getCityCentreLongitude());

    double h2Distance = HaversineDistance.haversine(hotel2.getLatitude(), hotel2.getLongitude(),
      hotel2.getCity().getCityCentreLatitude(), hotel2.getCity().getCityCentreLongitude());

    return h1Distance == h2Distance ? 0 : h1Distance > h2Distance ? 1 : -1;
  }
}
