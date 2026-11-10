package com.example.demo.modelo;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Metodo de pago configurable. Solo registra como se cobro; no integra ningun medio. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(name = "metodos_pago")
public class MetodoPago extends EntidadSincronizada {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 40)
	private String codigo;

	@Column(nullable = false, length = 80)
	private String nombre;

	/** true: suma al efectivo fisico de la caja. */
	@Column(name = "afecta_caja", nullable = false)
	private Boolean afectaCaja;

	@Column(nullable = false)
	private Boolean activo;

	@Column(nullable = false)
	private Integer orden;
}
