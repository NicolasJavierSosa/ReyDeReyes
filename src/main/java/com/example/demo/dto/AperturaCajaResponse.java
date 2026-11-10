package com.example.demo.dto;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AperturaCajaResponse(
	UUID id,
	LocalDateTime openedAt,
	BigDecimal baseAmount
) {}
