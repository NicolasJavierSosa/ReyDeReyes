package com.example.demo.dto;

import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductoRequest(
	@NotBlank String barcode,
	@NotBlank String name,
	String description,
	UUID categoryId,
	String supplierId,
	@NotBlank String unit,
	@NotBlank String type,
	@DecimalMin("0.0") BigDecimal cost,
	@NotNull @DecimalMin("0.0") BigDecimal price,
	@DecimalMin("0.0") BigDecimal stock,
	/** Stock que mostraba el formulario al abrirse. Si viene, solo se ajusta la diferencia. */
	BigDecimal stockOriginal,
	@DecimalMin("0.0") BigDecimal minStock,
	Boolean active
) {}
