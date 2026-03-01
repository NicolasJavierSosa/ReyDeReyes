package com.example.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductoRequest(
	@NotBlank String barcode,
	@NotBlank String name,
	String description,
	Long categoryId,
	@NotBlank String unit,
	@NotBlank String type,
	@DecimalMin("0.0") BigDecimal cost,
	@NotNull @DecimalMin("0.0") BigDecimal price,
	@NotNull @DecimalMin("0.0") BigDecimal stock,
	@DecimalMin("0.0") BigDecimal minStock,
	Boolean active
) {}
