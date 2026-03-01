package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record ProductoEstadoRequest(
	@NotNull Boolean active
) {}
