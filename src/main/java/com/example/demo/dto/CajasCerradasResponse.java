package com.example.demo.dto;

import java.util.List;

public record CajasCerradasResponse(
	List<CajaCerradaDto> items,
	int page,
	int size,
	boolean hasMore
) {}
