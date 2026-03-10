package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record ProveedorRequest(
	@NotBlank String phone,
	@NotBlank String name,
	Boolean active
) {}
