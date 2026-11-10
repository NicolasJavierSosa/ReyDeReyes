package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.demo.enums.EstadoVenta;

public record VentaResumenDto(
	UUID id,
	String ticket,
	LocalDateTime dateTime,
	BigDecimal total,
	EstadoVenta status,
	String paymentMethod,
	int items
) {}
