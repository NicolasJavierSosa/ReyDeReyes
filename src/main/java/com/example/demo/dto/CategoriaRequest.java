package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoriaRequest(
	@NotBlank String nombre,
	String descripcion
) {}
