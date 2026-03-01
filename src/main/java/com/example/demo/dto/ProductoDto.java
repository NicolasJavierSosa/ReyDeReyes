package com.example.demo.dto;

import java.math.BigDecimal;

public record ProductoDto(
	Long id,
	String barcode,
	String name,
	String description,
	Long categoryId,
	String categoryName,
	String unit,
	String type,
	BigDecimal cost,
	BigDecimal price,
	BigDecimal stock,
	BigDecimal minStock,
	Boolean active
) {}
