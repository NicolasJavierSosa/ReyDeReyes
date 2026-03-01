package com.example.demo.modelo;

import com.example.demo.enums.MetodoPago;
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
	name = "pagos",
	indexes = {
		@Index(name = "idx_pago_venta", columnList = "venta_id"),
		@Index(name = "idx_pago_metodo", columnList = "metodo")
	}
)
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "venta_id", nullable = false)
	private Venta venta;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MetodoPago metodo;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal monto;

	@Column(precision = 14, scale = 2)
	private BigDecimal entregado;

	@Column(precision = 14, scale = 2)
	private BigDecimal cambio;

	@Column(length = 120)
	private String referencia;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;
}
