package com.example.demo.modelo;

import com.example.demo.enums.TipoMovimientoStock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
	name = "movimientos_stock",
	indexes = {
		@Index(name = "idx_mov_stock_producto", columnList = "producto_id"),
		@Index(name = "idx_mov_stock_fecha_hora", columnList = "fecha_hora")
	}
)
public class MovimientoStock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "producto_id", nullable = false)
	private Producto producto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoMovimientoStock tipo;

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
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;
}
