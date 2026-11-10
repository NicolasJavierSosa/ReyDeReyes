package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record VentaCheckoutPagoRequest(
	/** Codigo del metodo de pago (EFECTIVO, TRANSFERENCIA, ...). */
	@NotBlank String method,
	@DecimalMin("0.0") BigDecimal amountReceived
) {}
