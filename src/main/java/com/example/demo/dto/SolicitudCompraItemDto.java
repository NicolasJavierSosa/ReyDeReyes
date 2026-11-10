package com.example.demo.dto;

import java.util.UUID;

import java.math.BigDecimal;

public record SolicitudCompraItemDto(
	UUID id,
	UUID productId,
	String productName,
	BigDecimal qtyRequested,
	BigDecimal qtyReceived
) {}
