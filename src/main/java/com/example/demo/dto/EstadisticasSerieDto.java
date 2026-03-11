package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

public record EstadisticasSerieDto(
	List<String> labels,
	List<BigDecimal> data
) {}
