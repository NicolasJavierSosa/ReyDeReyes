package com.example.demo.dto;

import java.util.UUID;

import java.math.BigDecimal;

public record ProductoVentaDto(
	UUID id,
	String barcode,
	String name,
	BigDecimal price,
	UUID categoryId,
	String categoryName
) {}
