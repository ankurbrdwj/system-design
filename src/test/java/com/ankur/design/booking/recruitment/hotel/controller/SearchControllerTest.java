package com.ankur.design.booking.recruitment.hotel.controller;

import com.ankur.design.booking.recruitment.hotel.repository.CityRepository;
import com.ankur.design.booking.recruitment.hotel.repository.HotelRepository;
import com.ankur.design.booking.recruitment.testing.SlowTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:data.sql")
@SlowTest
public class SearchControllerTest {
  @Autowired
  private MockMvc mockMvc;
  @Autowired private ObjectMapper mapper;

  @Autowired private HotelRepository repository;
  @Autowired private CityRepository cityRepository;
 // @Autowired private SearchService searchService;
  @Test
  @DisplayName("Test Search Hotel By cityId  API")
  void getSearchByCityId() throws Exception {
    mockMvc
      .perform(get("/search/{cityId}",1L))
      .andExpect(status().is2xxSuccessful())
      .andExpect(jsonPath("$", hasSize( 3)));
  }

}
