package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record ClienteEstadoRequest(
	@NotNull Boolean active
) {}
