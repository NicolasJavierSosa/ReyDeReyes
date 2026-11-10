package com.example.demo.servicio;

import static com.example.demo.util.Valores.conflicto;
import static com.example.demo.util.Valores.noEncontrado;
import static com.example.demo.util.Valores.requerido;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.ClienteDto;
import com.example.demo.dto.ClienteRequest;
import com.example.demo.modelo.Cliente;
import com.example.demo.repositorio.ClienteRepository;

@Service
public class ClienteService {

	private final ClienteRepository clienteRepository;

	public ClienteService(ClienteRepository clienteRepository) {
		this.clienteRepository = clienteRepository;
	}

	@Transactional(readOnly = true)
	public List<ClienteDto> listar(String status) {
		List<Cliente> clientes;
		if ("active".equalsIgnoreCase(status)) {
			clientes = clienteRepository.findByActivoOrderByApellidoAscNombreAsc(true);
		} else if ("inactive".equalsIgnoreCase(status)) {
			clientes = clienteRepository.findByActivoOrderByApellidoAscNombreAsc(false);
		} else {
			clientes = clienteRepository.findAllByOrderByApellidoAscNombreAsc();
		}
		return clientes.stream().map(this::toDto).toList();
	}

	@Transactional
	public ClienteDto crear(ClienteRequest request) {
		String email = email(request.email());
		if (clienteRepository.existsByEmailIgnoreCase(email)) {
			throw conflicto("Email existente");
		}
		Cliente cliente = new Cliente();
		cliente.setActivo(true);
		apply(cliente, request);
		return toDto(clienteRepository.save(cliente));
	}

	@Transactional
	public ClienteDto actualizar(UUID id, ClienteRequest request) {
		Cliente cliente = clienteRepository.findById(id)
			.orElseThrow(() -> noEncontrado("Cliente no encontrado"));
		if (clienteRepository.existsByEmailIgnoreCaseAndIdNot(email(request.email()), id)) {
			throw conflicto("Email existente");
		}
		apply(cliente, request);
		return toDto(clienteRepository.save(cliente));
	}

	@Transactional
	public ClienteDto cambiarEstado(UUID id, boolean active) {
		Cliente cliente = clienteRepository.findById(id)
			.orElseThrow(() -> noEncontrado("Cliente no encontrado"));
		cliente.setActivo(active);
		return toDto(clienteRepository.save(cliente));
	}

	private void apply(Cliente cliente, ClienteRequest request) {
		cliente.setNombre(requerido(request.firstName(), "Nombre requerido"));
		cliente.setApellido(requerido(request.lastName(), "Apellido requerido"));
		cliente.setTelefono(requerido(request.phone(), "Telefono requerido"));
		cliente.setEmail(email(request.email()));
	}

	private String email(String value) {
		return requerido(value, "Email requerido").toLowerCase(Locale.ROOT);
	}

	private ClienteDto toDto(Cliente cliente) {
		return new ClienteDto(
			cliente.getId(),
			cliente.getNombre(),
			cliente.getApellido(),
			cliente.getTelefono(),
			cliente.getEmail(),
			Boolean.TRUE.equals(cliente.getActivo())
		);
	}
}
