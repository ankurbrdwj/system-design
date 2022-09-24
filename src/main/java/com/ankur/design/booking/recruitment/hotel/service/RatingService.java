package com.ankur.design.booking.recruitment.hotel.service;



import com.ankur.design.booking.recruitment.hotel.dto.RatingReportDto;
import com.ankur.design.booking.recruitment.hotel.model.Hotel;

import java.util.List;

public interface RatingService {
    RatingReportDto getRatingAverage(Long cityId);

    RatingReportDto getRatingAverage(List<Hotel> hotels);
}
