package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductoDto(
	Long id,
	String barcode,
	String name,
	String description,
	Long categoryId,
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
	List<Long> comboItems,
	Long comboGroupCategoryId,
	String comboGroupCategoryName,
	Integer comboGroupQuantity
) {}
