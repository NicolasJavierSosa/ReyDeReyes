package com.example.demo.dto;

import java.util.UUID;

import java.util.List;

public record SolicitudCompraDto(
	UUID id,
	String supplierId,
	String supplierName,
	String status,
	String dateTime,
	List<SolicitudCompraItemDto> items
) {}
