package com.example.demo.dto;

import com.example.demo.enums.TipoMovimientoTesoreria;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record MovimientoManualRequest(
	@NotNull TipoMovimientoTesoreria type,
	@NotBlank String concept,
	String detail,
	@NotNull @DecimalMin("0.01") BigDecimal amount
) {}
