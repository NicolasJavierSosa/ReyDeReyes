package com.example.demo.modelo;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cliente del comercio. No es un usuario del sistema. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(name = "clientes")
public class Cliente extends EntidadSincronizada {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(nullable = false, length = 120)
	private String apellido;

	@Column(nullable = false, length = 32)
	private String telefono;

	@Column(nullable = false, length = 120)
	private String email;

	@Column(nullable = false)
	private Boolean activo;
}
