package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.demo.enums.TipoMovimientoStock;

public record MovimientoStockDto(
	UUID id,
	LocalDateTime dateTime,
	TipoMovimientoStock type,
	BigDecimal quantity,
	String reason,
	Long saleNumber
) {}
