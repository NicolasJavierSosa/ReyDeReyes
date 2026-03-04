package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ConteoBilleteRequest(
	@NotNull @DecimalMin("0.01") BigDecimal denomination,
	@NotNull @Min(0) Integer quantity
) {}
