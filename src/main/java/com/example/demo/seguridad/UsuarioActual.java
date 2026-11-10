package com.example.demo.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Nombre del usuario que esta operando (siempre el duenio), para dejarlo en los movimientos. */
public final class UsuarioActual {

	private static final String POR_DEFECTO = "Duenio";

	private UsuarioActual() {
	}

	public static String nombre() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof DuenioUserDetails duenio) {
			return duenio.getNombre();
		}
		return POR_DEFECTO;
	}
}
