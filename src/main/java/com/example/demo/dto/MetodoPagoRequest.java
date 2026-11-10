package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record MetodoPagoRequest(
	@NotBlank String code,
	@NotBlank String name,
	Boolean affectsCash,
	Boolean active,
	Integer order
) {}
