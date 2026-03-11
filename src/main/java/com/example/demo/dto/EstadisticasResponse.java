package com.example.demo.dto;

import java.util.List;

public record EstadisticasResponse(
	String period,
	String from,
	String to,
	EstadisticasKpiDto kpis,
	EstadisticasSerieDto ticketPromedio,
	EstadisticasSerieDto topProducts,
	EstadisticasSerieDto rotation,
	List<EstadisticasCategoriaDto> categories
) {}
