package com.ankur.design.booking.recruitment.hotel.controller;

import com.ankur.design.booking.recruitment.hotel.model.Hotel;
import com.ankur.design.booking.recruitment.hotel.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {
  private SearchService searchService;

  @Autowired
  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/{cityId}")
  ResponseEntity<List<Hotel>> searchByCity(@PathVariable("cityId") Long cityId
    , @RequestParam(required = false) String sortBy) {
    return new ResponseEntity<>(searchService.searchByCity(cityId, sortBy), HttpStatus.OK);
  }

}
