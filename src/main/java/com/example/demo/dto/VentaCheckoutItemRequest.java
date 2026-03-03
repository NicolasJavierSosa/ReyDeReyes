package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record VentaCheckoutItemRequest(
	@NotBlank String barcode,
	@NotNull @DecimalMin("0.001") BigDecimal quantity
) {}
