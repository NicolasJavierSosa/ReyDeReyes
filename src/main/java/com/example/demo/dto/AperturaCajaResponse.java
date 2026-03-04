package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AperturaCajaResponse(
	Long id,
	LocalDateTime openedAt,
	BigDecimal baseAmount
) {}
