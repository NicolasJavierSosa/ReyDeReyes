package com.example.demo.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "pagos")
public class Pago extends EntidadSincronizada {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "venta_id", nullable = false)
	private Venta venta;

	@ManyToOne(optional = false)
	@JoinColumn(name = "metodo_pago_id", nullable = false)
	private MetodoPago metodoPago;

	/** Nombre del metodo al momento del cobro, por si despues se renombra. */
	@Column(name = "metodo_nombre", nullable = false, length = 80)
	private String metodoNombre;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal monto;

	@Column(precision = 14, scale = 2)
	private BigDecimal entregado;

	@Column(precision = 14, scale = 2)
	private BigDecimal cambio;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;
}
