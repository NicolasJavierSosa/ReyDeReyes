package com.example.demo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder.Default;

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
		@Index(name = "idx_producto_categoria", columnList = "categoria_id"),
		@Index(name = "idx_producto_proveedor", columnList = "proveedor_id")
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

	@Column(nullable = false)
	private boolean combo;

	@ManyToOne
	@JoinColumn(name = "categoria_id")
	private Categoria categoria;

	@ManyToOne
	@JoinColumn(name = "unidad_medida_id")
	private UnidadMedida unidadMedida;

	@ManyToOne
	@JoinColumn(name = "tipo_id")
	private Tipo tipo;

	@ManyToOne
	@JoinColumn(name = "proveedor_id")
	private Proveedor proveedor;

	@ManyToMany
	@JoinTable(
		name = "combo_productos",
		joinColumns = @JoinColumn(name = "combo_id"),
		inverseJoinColumns = @JoinColumn(name = "producto_id")
	)
	@Default
	private List<Producto> comboProductos = new ArrayList<>();

	@ManyToOne
	@JoinColumn(name = "combo_grupo_categoria_id")
	private Categoria comboGrupoCategoria;

	@Column(name = "combo_grupo_cantidad")
	private Integer comboGrupoCantidad;

	@OneToOne(mappedBy = "producto")
	private Stock stock;


}
