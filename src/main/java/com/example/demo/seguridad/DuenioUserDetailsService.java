package com.example.demo.seguridad;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.repositorio.UsuarioRepository;

@Service
public class DuenioUserDetailsService implements UserDetailsService {

	private final UsuarioRepository usuarioRepository;

	public DuenioUserDetailsService(UsuarioRepository usuarioRepository) {
		this.usuarioRepository = usuarioRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		return usuarioRepository.findByUsernameIgnoreCase(username)
			.map(DuenioUserDetails::new)
			.orElseThrow(() -> new UsernameNotFoundException("Usuario inexistente"));
	}
}
