package com.example.demo.dto;

import java.util.List;

public record SolicitudCompraDto(
	Long id,
	String supplierId,
	String supplierName,
	String status,
	String dateTime,
	List<SolicitudCompraItemDto> items
) {}
