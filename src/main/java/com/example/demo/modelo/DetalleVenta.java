package com.example.demo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(
	name = "detalles_venta",
	indexes = {
		@Index(name = "idx_detalle_venta_venta", columnList = "venta_id"),
		@Index(name = "idx_detalle_venta_producto", columnList = "producto_id")
	}
)
public class DetalleVenta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "venta_id", nullable = false)
	private Venta venta;

	@ManyToOne(optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	@ManyToOne
	@JoinColumn(name = "descuento_id")
	private Descuento descuento;

	@Column(nullable = false, precision = 14, scale = 3)
	private BigDecimal cantidad;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal precioUnitario;

	@Column(precision = 14, scale = 2)
	private BigDecimal descuentoLinea;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal subtotal;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal totalLinea;
}
