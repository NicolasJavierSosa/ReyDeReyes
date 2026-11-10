package com.example.demo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "telefono", callSuper = false)
@Entity
@Table(name = "proveedores")
public class Proveedor extends EntidadSincronizada {

	/** Clave: numero de WhatsApp. */
	@Id
	@Column(length = 40)
	private String telefono;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(nullable = false)
	private Boolean activo;
}
