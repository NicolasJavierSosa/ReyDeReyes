package com.example.demo.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.demo.enums.EstadoVenta;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
@Table(name = "ventas")
public class Venta extends EntidadSincronizada {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** Numero correlativo visible. Solo lo genera el local. */
	@Column(nullable = false, unique = true)
	private Long numero;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal subtotal;

	@Column(name = "descuento_total", nullable = false, precision = 14, scale = 2)
	private BigDecimal descuentoTotal;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal total;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoVenta estado;

	@ManyToOne(optional = false)
	@JoinColumn(name = "caja_id", nullable = false)
	private Caja caja;

	@Column(length = 500)
	private String observaciones;

	@Column(name = "fecha_anulacion")
	private LocalDateTime fechaAnulacion;

	@OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
	@OrderBy("id")
	private List<DetalleVenta> detalles = new ArrayList<>();

	@OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
	private List<Pago> pagos = new ArrayList<>();
}
