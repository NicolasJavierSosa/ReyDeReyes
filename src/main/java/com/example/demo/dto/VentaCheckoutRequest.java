package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record VentaCheckoutRequest(
	@NotEmpty List<@Valid VentaCheckoutItemRequest> items,
	@DecimalMin("0.0") BigDecimal discount,
	@NotNull @Valid VentaCheckoutPagoRequest payment
) {}
