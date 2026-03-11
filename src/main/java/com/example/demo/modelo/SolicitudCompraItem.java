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
	name = "solicitudes_compra_items",
	indexes = {
		@Index(name = "idx_solicitud_item_solicitud", columnList = "solicitud_id"),
		@Index(name = "idx_solicitud_item_producto", columnList = "producto_id")
	}
)
public class SolicitudCompraItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "solicitud_id", nullable = false)
	private SolicitudCompra solicitud;

	@ManyToOne(optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	@Column(name = "cantidad_solicitada", nullable = false, precision = 14, scale = 3)
	private BigDecimal cantidadSolicitada;

	@Column(name = "cantidad_recibida", nullable = false, precision = 14, scale = 3)
	private BigDecimal cantidadRecibida;
}
