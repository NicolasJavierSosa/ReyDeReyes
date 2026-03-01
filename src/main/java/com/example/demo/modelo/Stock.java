package com.example.demo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "stock")
public class Stock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(optional = false)
	@JoinColumn(name = "producto_id", nullable = false, unique = true)
	private Producto producto;

	@Column(nullable = false, precision = 14, scale = 3)
	private BigDecimal cantidadActual;

	@Column(precision = 14, scale = 3)
	private BigDecimal stockMinimo;

	@Column(precision = 14, scale = 3)
	private BigDecimal stockMaximo;

	@Column(name = "ultimo_movimiento")
	private LocalDateTime ultimoMovimiento;

	@Version
	private Long version;
}
