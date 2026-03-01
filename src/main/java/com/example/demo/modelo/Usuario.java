package com.example.demo.modelo;

import com.example.demo.enums.RolUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
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
@EqualsAndHashCode(of = "id")
@Entity
@Table(
	name = "usuarios",
	indexes = {
		@Index(name = "idx_usuario_username", columnList = "username"),
		@Index(name = "idx_usuario_apellido", columnList = "apellido"),
		@Index(name = "idx_usuario_telefono", columnList = "telefono"),
		@Index(name = "idx_usuario_nombre_completo", columnList = "nombre_completo")
	}
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_usuario", discriminatorType = DiscriminatorType.STRING)
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nombreCompleto;

	@Column(nullable = false, unique = true, length = 80)
	private String username;

	@Column(nullable = false, length = 120)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RolUsuario rol;

	@Column(nullable = false)
	private Boolean activo;
}
