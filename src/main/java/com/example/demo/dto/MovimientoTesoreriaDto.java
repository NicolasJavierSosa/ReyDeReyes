package com.example.demo.dto;

import com.example.demo.enums.MetodoPago;
import com.example.demo.enums.TipoMovimientoTesoreria;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoTesoreriaDto(
	Long id,
	LocalDateTime dateTime,
	TipoMovimientoTesoreria type,
	String concept,
	String detail,
	String user,
	BigDecimal amount,
	MetodoPago paymentMethod,
	Long saleId,
	boolean affectsCash,
	boolean deletable,
	boolean voided
) {}
