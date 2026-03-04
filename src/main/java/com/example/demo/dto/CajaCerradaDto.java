package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CajaCerradaDto(
	Long id,
	LocalDateTime closedAt,
	BigDecimal expectedCash,
	BigDecimal countedCash,
	BigDecimal difference,
	String responsible,
	String notes
) {}
