package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record ProveedorEstadoRequest(@NotNull Boolean active) {}
