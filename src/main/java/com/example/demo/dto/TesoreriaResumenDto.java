package com.example.demo.dto;

import java.math.BigDecimal;

public record TesoreriaResumenDto(
	BigDecimal incomes,
	BigDecimal sales,
	BigDecimal expenses,
	BigDecimal cashInDrawer
) {}
