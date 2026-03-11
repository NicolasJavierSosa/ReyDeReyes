package com.example.demo.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SolicitudCompraRecepcionRequest(
	@NotEmpty List<SolicitudCompraRecepcionItemRequest> items
) {}
