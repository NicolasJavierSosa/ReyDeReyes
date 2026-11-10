package com.example.demo.dto;

import java.util.UUID;

import com.example.demo.enums.EstadoVenta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VentaDetalleResponse(
	UUID id,
	String ticket,
	LocalDateTime dateTime,
	BigDecimal subtotal,
	BigDecimal discount,
	BigDecimal total,
	EstadoVenta status,
	String paymentMethod,
	List<VentaDetalleItemDto> items
) {}
