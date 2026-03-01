package com.example.demo.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@DiscriminatorValue("VENDEDOR")
public class Vendedor extends Usuario {

	@Column(length = 32)
	private String telefono;

	@Column(length = 120)
	private String email;
}
