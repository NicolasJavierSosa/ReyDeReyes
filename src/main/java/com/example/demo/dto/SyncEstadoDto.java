package com.example.demo.dto;

import java.time.OffsetDateTime;

/**
 * Estado de la sincronizacion para el indicador de la barra superior.
 * level: VERDE (al dia), AMARILLO (hay cambios sin subir), ROJO (sin subir hace mas de 1 hora),
 * DESACTIVADA (no hay conexion a la nube configurada).
 */
public record SyncEstadoDto(
	String level,
	boolean enabled,
	long pendingChanges,
	long unreviewedConflicts,
	OffsetDateTime lastSuccess,
	OffsetDateTime lastAttempt,
	String lastError
) {}
