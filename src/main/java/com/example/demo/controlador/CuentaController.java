package com.example.demo.controlador;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.CambioClaveRequest;
import com.example.demo.modelo.Usuario;
import com.example.demo.repositorio.UsuarioRepository;
import com.example.demo.seguridad.DuenioUserDetails;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cuenta")
public class CuentaController {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;

	public CuentaController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping
	public Map<String, String> actual(@AuthenticationPrincipal DuenioUserDetails duenio) {
		return Map.of("username", duenio.getUsername(), "name", duenio.getNombre());
	}

	@PostMapping("/clave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void cambiarClave(@AuthenticationPrincipal DuenioUserDetails duenio, @Valid @RequestBody CambioClaveRequest request) {
		Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(duenio.getUsername())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
		if (!passwordEncoder.matches(request.currentPassword(), usuario.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave actual no es correcta");
		}
		usuario.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		usuarioRepository.save(usuario);
	}
}
