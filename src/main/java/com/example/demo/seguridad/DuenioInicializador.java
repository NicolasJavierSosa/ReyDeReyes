package com.example.demo.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.modelo.Usuario;
import com.example.demo.repositorio.UsuarioRepository;

/**
 * Crea el usuario del duenio la primera vez que arranca el sistema, con los datos de
 * las variables REYDEREYES_DUENIO_USUARIO / REYDEREYES_DUENIO_CLAVE. Despues la clave
 * se cambia desde la aplicacion (POST /api/cuenta/clave).
 */
@Component
public class DuenioInicializador implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DuenioInicializador.class);

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final String usuario;
	private final String clave;
	private final String nombre;

	public DuenioInicializador(
		UsuarioRepository usuarioRepository,
		PasswordEncoder passwordEncoder,
		@Value("${app.duenio.usuario:duenio}") String usuario,
		@Value("${app.duenio.clave:}") String clave,
		@Value("${app.duenio.nombre:Duenio}") String nombre
	) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.usuario = usuario;
		this.clave = clave;
		this.nombre = nombre;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (usuarioRepository.count() > 0) {
			return;
		}
		String claveInicial = clave == null || clave.isBlank() ? "reydereyes" : clave;
		Usuario duenio = new Usuario();
		duenio.setUsername(usuario.trim());
		duenio.setPasswordHash(passwordEncoder.encode(claveInicial));
		duenio.setNombre(nombre.trim());
		duenio.setActivo(true);
		usuarioRepository.save(duenio);
		if (clave == null || clave.isBlank()) {
			log.warn("Se creo el usuario '{}' con la clave inicial por defecto. Cambiela desde la aplicacion.", usuario);
		} else {
			log.info("Se creo el usuario del duenio '{}'", usuario);
		}
	}
}
