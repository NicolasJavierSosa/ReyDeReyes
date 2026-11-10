package com.example.demo.seguridad;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import com.example.demo.modelo.Usuario;

public class DuenioUserDetails extends User {

	private final String nombre;

	public DuenioUserDetails(Usuario usuario) {
		super(usuario.getUsername(), usuario.getPasswordHash(), Boolean.TRUE.equals(usuario.getActivo()),
			true, true, true, List.of(new SimpleGrantedAuthority("ROLE_DUENIO")));
		this.nombre = usuario.getNombre();
	}

	public String getNombre() {
		return nombre;
	}
}
