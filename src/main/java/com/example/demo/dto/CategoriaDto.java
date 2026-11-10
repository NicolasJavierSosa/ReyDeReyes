package com.example.demo.dto;

import java.util.UUID;

public record CategoriaDto(
	UUID id,
	String nombre,
	Boolean activa
) {}
