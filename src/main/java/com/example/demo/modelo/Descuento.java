package com.example.demo.modelo;

import com.example.demo.enums.TipoDescuento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
	name = "descuentos",
	indexes = {
		@Index(name = "idx_descuento_nombre", columnList = "nombre"),
		@Index(name = "idx_descuento_activo", columnList = "activo")
	}
)
public class Descuento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoDescuento tipo;

	@Column(nullable = false, precision = 14, scale = 4)
	private BigDecimal valor;

	@Column(nullable = false)
	private Boolean activo;
}
