package com.example.demo.modelo;

import com.example.demo.enums.EstadoVenta;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
	name = "ventas",
	indexes = {
		@Index(name = "idx_venta_fecha_hora", columnList = "fecha_hora"),
		@Index(name = "idx_venta_estado", columnList = "estado"),
		@Index(name = "idx_venta_caja", columnList = "caja_id"),
		@Index(name = "idx_venta_vendedor", columnList = "vendedor_id"),
	}
)
public class Venta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal subtotal;

	@Column(precision = 14, scale = 2)
	private BigDecimal descuentoTotal;


	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal total;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoVenta estado;

	@ManyToOne(optional = false)
	@JoinColumn(name = "vendedor_id", nullable = false)
	private Vendedor vendedor;

	@ManyToOne(optional = false)
	@JoinColumn(name = "caja_id", nullable = false)
	private Caja caja;

	@ManyToOne
	@JoinColumn(name = "descuento_id")
	private Descuento descuento;

	@Column(length = 500)
	private String observaciones;

	@OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<DetalleVenta> detalles = new ArrayList<>();

	@OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<Pago> pagos = new ArrayList<>();
}
