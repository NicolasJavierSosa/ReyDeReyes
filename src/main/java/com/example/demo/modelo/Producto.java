package com.example.demo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

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
	name = "productos",
	indexes = {
		@Index(name = "idx_producto_nombre", columnList = "nombre"),
		@Index(name = "idx_producto_categoria", columnList = "categoria_id")
	}
)
public class Producto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "codigo_barra", unique = true, length = 64)
	private String codigoBarra;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 500)
	private String descripcion;

	@Column(precision = 14, scale = 2)
	private BigDecimal precioCosto;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal precioVenta;

	@Column(nullable = false)
	private Boolean activo;

	@Column(nullable = false)
	private LocalDate fechaVencimiento;

	@ManyToOne
	@JoinColumn(name = "categoria_id")
	private Categoria categoria;

	@ManyToOne
	@JoinColumn(name = "unidad_medida_id")
	private UnidadMedida unidadMedida;

	@ManyToOne
	@JoinColumn(name = "tipo_id")
	private Tipo tipo;

	@OneToOne(mappedBy = "producto")
	private Stock stock;


}
