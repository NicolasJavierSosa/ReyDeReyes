package com.example.demo.dto;

import java.util.UUID;

import com.example.demo.enums.TipoMovimientoTesoreria;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoTesoreriaDto(
	UUID id,
	LocalDateTime dateTime,
	TipoMovimientoTesoreria type,
	String concept,
	String detail,
	String user,
	BigDecimal amount,
	String paymentMethod,
	UUID saleId,
	boolean affectsCash,
	boolean deletable,
	boolean voided
) {}
