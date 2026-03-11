package com.example.demo.dto;

import java.math.BigDecimal;

public record SolicitudCompraItemDto(
	Long id,
	Long productId,
	String productName,
	BigDecimal qtyRequested,
	BigDecimal qtyReceived
) {}
