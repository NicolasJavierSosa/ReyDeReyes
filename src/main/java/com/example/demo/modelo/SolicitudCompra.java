package com.example.demo.modelo;

import com.example.demo.enums.EstadoSolicitudCompra;
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
	name = "solicitudes_compra",
	indexes = {
		@Index(name = "idx_solicitud_compra_fecha", columnList = "fecha_hora"),
		@Index(name = "idx_solicitud_compra_estado", columnList = "estado"),
		@Index(name = "idx_solicitud_compra_proveedor", columnList = "proveedor_id")
	}
)
public class SolicitudCompra {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "proveedor_id", nullable = false)
	private Proveedor proveedor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoSolicitudCompra estado;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;

	@OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<SolicitudCompraItem> items = new ArrayList<>();
}
