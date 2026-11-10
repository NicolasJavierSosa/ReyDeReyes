package com.example.demo.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.demo.enums.TipoMovimientoStock;

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

/** Cada entrada o salida de stock. La cantidad actual es la suma de estos movimientos. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(name = "movimientos_stock")
public class MovimientoStock extends EntidadSincronizada {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoMovimientoStock tipo;

	/** Positiva = entra stock, negativa = sale stock. */
	@Column(nullable = false, precision = 14, scale = 3)
	private BigDecimal cantidad;

	@Column(length = 255)
	private String motivo;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;

	@ManyToOne
	@JoinColumn(name = "venta_id")
	private Venta venta;

	@ManyToOne
	@JoinColumn(name = "solicitud_compra_id")
	private SolicitudCompra solicitudCompra;
}
