package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CierreCajaResponse(
	BigDecimal expectedCash,
	BigDecimal countedCash,
	BigDecimal difference,
	LocalDateTime closedAt
) {}
