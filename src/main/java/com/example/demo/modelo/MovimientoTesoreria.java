package com.example.demo.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.demo.enums.TipoMovimientoTesoreria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "movimientos_tesoreria")
public class MovimientoTesoreria extends EntidadSincronizada {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoMovimientoTesoreria tipo;

	@Column(nullable = false, length = 120)
	private String concepto;

	@Column(length = 500)
	private String detalle;

	/** Con signo: los egresos son negativos. */
	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false)
	private Boolean anulado;

	@ManyToOne(optional = false)
	@JoinColumn(name = "caja_id", nullable = false)
	private Caja caja;

	@Column(name = "afecta_caja", nullable = false)
	private Boolean afectaCaja;

	@ManyToOne
	@JoinColumn(name = "metodo_pago_id")
	private MetodoPago metodoPago;

	@Column(name = "metodo_nombre", length = 80)
	private String metodoNombre;

	@ManyToOne
	@JoinColumn(name = "venta_id")
	private Venta venta;

	@Column(length = 120)
	private String usuario;
}
