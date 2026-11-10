package com.example.demo.dto;

import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record ComboRequest(
	String barcode,
	@NotBlank String name,
	String description,
	UUID categoryId,
	@NotBlank String unit,
	@NotBlank String type,
	@DecimalMin("0.0") BigDecimal cost,
	@NotNull @DecimalMin("0.0") BigDecimal price,
	@DecimalMin("0.0") BigDecimal stock,
	@DecimalMin("0.0") BigDecimal minStock,
	Boolean active,
	List<UUID> items,
	UUID groupCategoryId,
	Integer groupQuantity
) {}
