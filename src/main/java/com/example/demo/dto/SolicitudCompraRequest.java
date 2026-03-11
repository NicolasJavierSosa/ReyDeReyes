package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SolicitudCompraRequest(
	@NotBlank String supplierId,
	@NotEmpty List<SolicitudCompraItemRequest> items
) {}
