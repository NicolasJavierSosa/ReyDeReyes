package com.example.demo.dto;

import java.util.UUID;

import java.math.BigDecimal;
import java.util.List;

public record ProductoDto(
	UUID id,
	String barcode,
	String name,
	String description,
	UUID categoryId,
	String categoryName,
	String supplierId,
	String supplierName,
	String unit,
	String type,
	BigDecimal cost,
	BigDecimal price,
	BigDecimal stock,
	BigDecimal minStock,
	Boolean active,
	Boolean combo,
	List<UUID> comboItems,
	UUID comboGroupCategoryId,
	String comboGroupCategoryName,
	Integer comboGroupQuantity
) {}
