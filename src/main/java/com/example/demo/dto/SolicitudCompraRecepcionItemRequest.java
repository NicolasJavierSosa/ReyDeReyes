package com.example.demo.dto;

import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SolicitudCompraRecepcionItemRequest(
	@NotNull UUID itemId,
	@NotNull @DecimalMin("0.0") BigDecimal receivedQty
) {}
