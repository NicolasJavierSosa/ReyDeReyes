package com.example.demo.dto;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VentaCheckoutResponse(
	UUID saleId,
	String ticket,
	BigDecimal subtotal,
	BigDecimal discount,
	BigDecimal total,
	String paymentMethod,
	BigDecimal amountReceived,
	BigDecimal change,
	LocalDateTime dateTime
) {}
