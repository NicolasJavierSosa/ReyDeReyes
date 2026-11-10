package com.example.demo.dto;

import java.util.UUID;

public record MetodoPagoDto(
	UUID id,
	String code,
	String name,
	boolean affectsCash,
	boolean active,
	int order
) {}
