package com.example.demo.dto;

import java.math.BigDecimal;

public record EstadisticasCategoriaDto(
	String name,
	BigDecimal qty,
	BigDecimal revenue,
	BigDecimal cost,
	BigDecimal margin
) {}
