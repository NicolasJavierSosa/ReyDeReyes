package com.example.demo.modelo;

import com.example.demo.enums.EstadoCaja;
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
import jakarta.persistence.Version;
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
	name = "cajas",
	indexes = {
		@Index(name = "idx_caja_fecha_apertura", columnList = "fecha_apertura"),
		@Index(name = "idx_caja_estado", columnList = "estado"),
		@Index(name = "idx_caja_responsable", columnList = "responsable_id")
	}
)
public class Caja {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fecha_apertura", nullable = false)
	private LocalDateTime fechaApertura;

	@Column(name = "fecha_cierre")
	private LocalDateTime fechaCierre;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal montoApertura;

	@Column(precision = 14, scale = 2)
	private BigDecimal totalIngresos;

	@Column(precision = 14, scale = 2)
	private BigDecimal totalEgresos;

	@Column(precision = 14, scale = 2)
	private BigDecimal totalVentasEfectivo;

	@Column(precision = 14, scale = 2)
	private BigDecimal totalVentasTarjeta;

	@Column(precision = 14, scale = 2)
	private BigDecimal totalVentasTransferencia;

	@Column(precision = 14, scale = 2)
	private BigDecimal montoCierreCalculado;

	@Column(precision = 14, scale = 2)
	private BigDecimal montoCierreReal;

	@Column(precision = 14, scale = 2)
	private BigDecimal diferencia;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoCaja estado;

	@Column(length = 500)
	private String observaciones;

	@ManyToOne(optional = false)
	@JoinColumn(name = "responsable_id", nullable = false)
	private Usuario responsable;

	@OneToMany(mappedBy = "caja", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<Venta> ventas = new ArrayList<>();

	@Version
	private Long version;
}
