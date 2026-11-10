package com.example.demo.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.demo.enums.EstadoCaja;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(name = "cajas")
public class Caja extends EntidadSincronizada {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "fecha_apertura", nullable = false)
	private LocalDateTime fechaApertura;

	@Column(name = "fecha_cierre")
	private LocalDateTime fechaCierre;

	@Column(name = "monto_apertura", nullable = false, precision = 14, scale = 2)
	private BigDecimal montoApertura;

	@Column(name = "efectivo_esperado", precision = 14, scale = 2)
	private BigDecimal efectivoEsperado;

	@Column(name = "efectivo_contado", precision = 14, scale = 2)
	private BigDecimal efectivoContado;

	@Column(precision = 14, scale = 2)
	private BigDecimal diferencia;

	@Column(name = "detalle_cierre", length = 2000)
	private String detalleCierre;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoCaja estado;

	@Column(length = 500)
	private String observaciones;
}
