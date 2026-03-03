package com.example.demo.modelo;

import com.example.demo.enums.MetodoPago;
import com.example.demo.enums.TipoMovimientoTesoreria;
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
	name = "movimientos_tesoreria",
	indexes = {
		@Index(name = "idx_mov_tes_fecha_hora", columnList = "fecha_hora"),
		@Index(name = "idx_mov_tes_tipo", columnList = "tipo"),
		@Index(name = "idx_mov_tes_venta", columnList = "venta_id")
	}
)
public class MovimientoTesoreria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoMovimientoTesoreria tipo;

	@Column(nullable = false, length = 120)
	private String concepto;

	@Column(length = 500)
	private String detalle;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal monto;

	@Column(name = "afecta_caja", nullable = false)
	private Boolean afectaCaja;

	@Enumerated(EnumType.STRING)
	@Column(name = "metodo_pago", length = 20)
	private MetodoPago metodoPago;

	@Column(length = 120)
	private String usuario;

	@ManyToOne
	@JoinColumn(name = "venta_id")
	private Venta venta;
}
