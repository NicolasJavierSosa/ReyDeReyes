package com.example.demo.dto;

import java.math.BigDecimal;

public record VentaDetalleItemDto(
	String barcode,
	String name,
	BigDecimal quantity,
	BigDecimal unitPrice,
	BigDecimal subtotal,
	BigDecimal totalLine
) {}
