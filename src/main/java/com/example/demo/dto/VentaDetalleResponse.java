package com.example.demo.dto;

import com.example.demo.enums.EstadoVenta;
import com.example.demo.enums.MetodoPago;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VentaDetalleResponse(
	Long id,
	String ticket,
	LocalDateTime dateTime,
	BigDecimal subtotal,
	BigDecimal discount,
	BigDecimal total,
	EstadoVenta status,
	MetodoPago paymentMethod,
	List<VentaDetalleItemDto> items
) {}
