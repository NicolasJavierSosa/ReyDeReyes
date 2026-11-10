package com.example.demo.modelo;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Columnas de control comunes a todas las tablas que se sincronizan con la nube.
 * actualizado_en y origen los completa el trigger de la base; la version la
 * maneja Hibernate (bloqueo optimista) y se usa para detectar conflictos.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class EntidadSincronizada {

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "actualizado_en", insertable = false, updatable = false)
	private OffsetDateTime actualizadoEn;

	@Column(insertable = false, updatable = false, length = 10)
	private String origen;
}
