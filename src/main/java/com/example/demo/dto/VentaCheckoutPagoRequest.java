package com.example.demo.dto;

import com.example.demo.enums.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record VentaCheckoutPagoRequest(
	@NotNull MetodoPago method,
	@DecimalMin("0.0") BigDecimal amountReceived
) {}
