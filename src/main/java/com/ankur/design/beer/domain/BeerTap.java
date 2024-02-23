package com.ankur.design.beer.domain;

import jakarta.persistence.Entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class BeerTap {
  private static final long serialVersionUID = 3252591505023324236L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

}
