package com.example.demo.modelo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "detalles_venta")
public class DetalleVenta extends EntidadSincronizada {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "venta_id", nullable = false)
	private Venta venta;

	@ManyToOne(optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	@Column(nullable = false, precision = 14, scale = 3)
	private BigDecimal cantidad;

	@Column(name = "precio_unitario", nullable = false, precision = 14, scale = 2)
	private BigDecimal precioUnitario;

	/** Costo del producto al momento de la venta (para margenes historicos). */
	@Column(name = "costo_unitario", nullable = false, precision = 14, scale = 2)
	private BigDecimal costoUnitario;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal subtotal;

	/** Parte del descuento general que le toca a esta linea. */
	@Column(name = "descuento_linea", nullable = false, precision = 14, scale = 2)
	private BigDecimal descuentoLinea;

	@Column(name = "total_linea", nullable = false, precision = 14, scale = 2)
	private BigDecimal totalLinea;

	/** En combos: componentes que se descontaron del stock. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<ComboItem> componentes;
}
