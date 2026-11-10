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
@EqualsAndHashCode(of = "abreviatura", callSuper = false)
@Entity
@Table(name = "unidades_medida")
public class UnidadMedida extends EntidadSincronizada {

	@Id
	@Column(length = 10)
	private String abreviatura;

	@Column(nullable = false, length = 80)
	private String nombre;
}
