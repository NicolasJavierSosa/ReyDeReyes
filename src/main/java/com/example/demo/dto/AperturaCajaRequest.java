package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AperturaCajaRequest(
	@NotNull @DecimalMin("0") BigDecimal amount,
	String notes
) {}
