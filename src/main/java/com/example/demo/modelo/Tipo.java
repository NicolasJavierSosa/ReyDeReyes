package com.example.demo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tipo de producto. La clave es el nombre en minusculas (igual en local y nube). */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(name = "tipos")
public class Tipo extends EntidadSincronizada {

	@Id
	@Column(length = 120)
	private String id;

	@Column(nullable = false, length = 120)
	private String nombre;
}
