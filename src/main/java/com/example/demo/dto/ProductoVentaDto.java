package com.example.demo.dto;

import java.math.BigDecimal;

public record ProductoVentaDto(
	Long id,
	String barcode,
	String name,
	BigDecimal price,
	Long categoryId,
	String categoryName
) {}
