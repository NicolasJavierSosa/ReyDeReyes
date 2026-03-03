package com.example.demo.dto;

import com.example.demo.enums.MetodoPago;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VentaCheckoutResponse(
	Long saleId,
	String ticket,
	BigDecimal subtotal,
	BigDecimal discount,
	BigDecimal total,
	MetodoPago paymentMethod,
	BigDecimal amountReceived,
	BigDecimal change,
	LocalDateTime dateTime
) {}
