package com.example.demo.servicio;

import com.example.demo.dto.ClienteDto;
import com.example.demo.dto.ClienteRequest;
import com.example.demo.enums.RolUsuario;
import com.example.demo.modelo.Cliente;
import com.example.demo.modelo.Configuracion;
import com.example.demo.repositorio.ClienteRepository;
import com.example.demo.repositorio.ConfiguracionRepository;
import com.example.demo.repositorio.UsuarioRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClienteService {

	private static final String FORM_LINK_KEY = "clientes.form.link";

	private final ClienteRepository clienteRepository;
	private final UsuarioRepository usuarioRepository;
	private final ConfiguracionRepository configuracionRepository;

	public ClienteService(
		ClienteRepository clienteRepository,
		UsuarioRepository usuarioRepository,
		ConfiguracionRepository configuracionRepository
	) {
		this.clienteRepository = clienteRepository;
		this.usuarioRepository = usuarioRepository;
		this.configuracionRepository = configuracionRepository;
	}

	@Transactional(readOnly = true)
	public List<ClienteDto> listar(String status) {
		List<Cliente> clientes;
		if ("active".equalsIgnoreCase(status)) {
			clientes = clienteRepository.findByActivoTrueOrderByNombreCompletoAsc();
		} else if ("inactive".equalsIgnoreCase(status)) {
			clientes = clienteRepository.findByActivoFalseOrderByNombreCompletoAsc();
		} else {
			clientes = clienteRepository.findAllByOrderByNombreCompletoAsc();
		}

		return clientes.stream()
			.map(this::toDto)
			.toList();
	}

	@Transactional
	public ClienteDto crear(ClienteRequest request) {
		String email = normalizeEmail(request.email(), true);
		if (clienteRepository.existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email existente");
		}

		Cliente cliente = new Cliente();
		cliente.setRol(RolUsuario.CLIENTE);
		cliente.setActivo(true);
		cliente.setUsername(generateUsername(email));
		cliente.setPassword(generatePassword());
		apply(cliente, request);

		return toDto(clienteRepository.save(cliente));
	}

	@Transactional
	public ClienteDto actualizar(Long id, ClienteRequest request) {
		Cliente cliente = clienteRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

		String email = normalizeEmail(request.email(), true);
		if (clienteRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email existente");
		}

		apply(cliente, request);
		return toDto(clienteRepository.save(cliente));
	}

	@Transactional
	public ClienteDto cambiarEstado(Long id, boolean active) {
		Cliente cliente = clienteRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
		cliente.setActivo(active);
		return toDto(clienteRepository.save(cliente));
	}

	@Transactional
	public ClienteDto registrarDesdeFormulario(ClienteRequest request) {
		String email = normalizeEmail(request.email(), true);
		Cliente cliente = clienteRepository.findFirstByEmailIgnoreCase(email).orElse(null);

		boolean creating = cliente == null;
		if (creating) {
			cliente = new Cliente();
			cliente.setRol(RolUsuario.CLIENTE);
			cliente.setActivo(true);
			cliente.setUsername(generateUsername(email));
			cliente.setPassword(generatePassword());
		}

		apply(cliente, request);
		return toDto(clienteRepository.save(cliente));
	}

	@Transactional(readOnly = true)
	public String obtenerLinkFormulario() {
		return configuracionRepository.findByClave(FORM_LINK_KEY)
			.map(Configuracion::getValor)
			.orElse("");
	}

	@Transactional
	public String guardarLinkFormulario(String link) {
		String normalized = normalizeText(link);
		if (normalized.isEmpty()) {
			configuracionRepository.findByClave(FORM_LINK_KEY)
				.ifPresent(configuracionRepository::delete);
			return "";
		}

		Configuracion configuracion = configuracionRepository.findByClave(FORM_LINK_KEY)
			.orElseGet(() -> new Configuracion(null, FORM_LINK_KEY, null));
		configuracion.setValor(normalized);
		configuracionRepository.save(configuracion);
		return normalized;
	}

	private void apply(Cliente cliente, ClienteRequest request) {
		String firstName = normalizeRequired(request.firstName(), "Nombre requerido");
		String lastName = normalizeRequired(request.lastName(), "Apellido requerido");
		String phone = normalizeRequired(request.phone(), "Telefono requerido");
		String email = normalizeEmail(request.email(), true);

		String nombreCompleto = (firstName + " " + lastName).trim();
		cliente.setNombre(firstName);
		cliente.setApellido(lastName);
		cliente.setNombreCompleto(nombreCompleto);
		cliente.setTelefono(phone);
		cliente.setEmail(email);
		cliente.setDocumento(null);
		cliente.setDireccion(null);
		cliente.setNotas(null);
	}

	private ClienteDto toDto(Cliente cliente) {
		String firstName = normalizeText(cliente.getNombre());
		String lastName = normalizeText(cliente.getApellido());

		if (firstName.isEmpty()) {
			String full = normalizeText(cliente.getNombreCompleto());
			if (!full.isEmpty()) {
				String[] parts = full.split("\\s+", 2);
				firstName = parts[0];
				if (lastName.isEmpty() && parts.length > 1) {
					lastName = parts[1];
				}
			}
		}

		return new ClienteDto(
			cliente.getId(),
			firstName,
			lastName,
			normalizeText(cliente.getTelefono()),
			normalizeText(cliente.getEmail()),
			Boolean.TRUE.equals(cliente.getActivo())
		);
	}

	private String generateUsername(String email) {
		String seed = normalizeText(email);
		if (seed.isEmpty()) {
			seed = String.valueOf(System.currentTimeMillis());
		}

		String slug = seed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
		if (slug.length() > 60) {
			slug = slug.substring(0, 60);
		}
		if (slug.isEmpty()) {
			slug = String.valueOf(System.currentTimeMillis());
		}

		String base = "cliente-" + slug;
		String candidate = base;
		int suffix = 1;
		while (usuarioRepository.existsByUsername(candidate)) {
			candidate = base + "-" + suffix;
			suffix++;
		}
		return candidate;
	}

	private String generatePassword() {
		return UUID.randomUUID().toString();
	}

	private String normalizeEmail(String value, boolean required) {
		String normalized = normalizeText(value);
		if (normalized.isEmpty()) {
			if (required) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email requerido");
			}
			return "";
		}
		return normalized.toLowerCase(Locale.ROOT);
	}

	private String normalizeRequired(String value, String message) {
		String normalized = normalizeText(value);
		if (normalized.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		}
		return normalized;
	}

	private String normalizeText(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}
}
