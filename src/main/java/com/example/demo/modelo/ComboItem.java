package com.example.demo.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Componente de un combo: producto y cuantas unidades lleva. Se guarda como JSON. */
public record ComboItem(UUID productoId, BigDecimal cantidad) {
}
