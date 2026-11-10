package com.example.demo.modelo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "productos")
public class Producto extends EntidadSincronizada {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "codigo_barra", nullable = false, unique = true, length = 64)
	private String codigoBarra;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 500)
	private String descripcion;

	@Column(name = "precio_costo", nullable = false, precision = 14, scale = 2)
	private BigDecimal precioCosto;

	@Column(name = "precio_venta", nullable = false, precision = 14, scale = 2)
	private BigDecimal precioVenta;

	@Column(nullable = false)
	private Boolean activo;

	@Column(name = "stock_minimo", nullable = false, precision = 14, scale = 3)
	private BigDecimal stockMinimo;

	@ManyToOne
	@JoinColumn(name = "categoria_id")
	private Categoria categoria;

	@ManyToOne
	@JoinColumn(name = "unidad_medida")
	private UnidadMedida unidadMedida;

	@ManyToOne
	@JoinColumn(name = "tipo_id")
	private Tipo tipo;

	@ManyToOne
	@JoinColumn(name = "proveedor_id")
	private Proveedor proveedor;

	@Column(nullable = false)
	private boolean combo;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "combo_items", nullable = false, columnDefinition = "jsonb")
	private List<ComboItem> comboItems = new ArrayList<>();

	@ManyToOne
	@JoinColumn(name = "combo_grupo_categoria_id")
	private Categoria comboGrupoCategoria;

	@Column(name = "combo_grupo_cantidad")
	private Integer comboGrupoCantidad;

	public boolean isComboDeGrupo() {
		return combo && comboGrupoCategoria != null && comboGrupoCantidad != null && comboGrupoCantidad > 0;
	}
}
