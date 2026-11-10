package com.example.demo.dto;

import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SolicitudCompraItemRequest(
	@NotNull UUID productId,
	@NotNull @DecimalMin("0.001") BigDecimal qty
) {}
