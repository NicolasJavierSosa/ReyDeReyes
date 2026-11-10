package com.example.demo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Corrige el stock de un producto: se registra un movimiento AJUSTE por la diferencia. */
public record AjusteStockRequest(
	@NotNull @DecimalMin("0.0") BigDecimal newStock,
	String reason
) {}
