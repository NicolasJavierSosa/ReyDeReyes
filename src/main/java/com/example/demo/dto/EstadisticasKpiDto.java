package com.example.demo.dto;

import java.math.BigDecimal;

public record EstadisticasKpiDto(
	BigDecimal revenue,
	BigDecimal profit,
	BigDecimal items,
	int salesCount,
	EstadisticasTrendDto trends
) {}
