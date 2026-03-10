package com.example.demo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
@EqualsAndHashCode(of = "telefono")
@Entity
@Table(
	name = "proveedores",
	indexes = {
		@Index(name = "idx_proveedor_nombre", columnList = "nombre")
	}
)
public class Proveedor {

	@Id
	@Column(length = 40)
	private String telefono; // PK: número de WhatsApp

	@Column(nullable = false, length = 120)
	private String nombre; // Empresa/Distribuidor

	@Column(nullable = false)
	private Boolean activo;
}
