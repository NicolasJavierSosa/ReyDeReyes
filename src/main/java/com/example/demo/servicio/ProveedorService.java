package com.example.demo.servicio;

import com.example.demo.dto.ProveedorDto;
import com.example.demo.dto.ProveedorRequest;
import com.example.demo.modelo.Proveedor;
import com.example.demo.repositorio.ProveedorRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProveedorService {

	private final ProveedorRepository proveedorRepository;

	public ProveedorService(ProveedorRepository proveedorRepository) {
		this.proveedorRepository = proveedorRepository;
	}

	@Transactional(readOnly = true)
	public List<ProveedorDto> listar(String status) {
		List<Proveedor> proveedores;
		if ("active".equalsIgnoreCase(status)) {
			proveedores = proveedorRepository.findByActivoTrueOrderByNombreAsc();
		} else if ("inactive".equalsIgnoreCase(status)) {
			proveedores = proveedorRepository.findByActivoFalseOrderByNombreAsc();
		} else {
			proveedores = proveedorRepository.findAllByOrderByNombreAsc();
		}

		return proveedores.stream()
			.map(this::toDto)
			.toList();
	}

	@Transactional
	public ProveedorDto crear(ProveedorRequest request) {
		String telefono = normalizeRequired(request.phone(), "Telefono requerido");
		if (proveedorRepository.existsById(telefono)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Telefono existente");
		}
		Proveedor proveedor = new Proveedor();
		proveedor.setTelefono(telefono);
		apply(proveedor, request, true);
		return toDto(proveedorRepository.save(proveedor));
	}

	@Transactional
	public ProveedorDto actualizar(String id, ProveedorRequest request) {
		Proveedor proveedor = proveedorRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));

		String telefono = normalizeRequired(request.phone(), "Telefono requerido");
		if (!telefono.equals(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede cambiar el telefono");
		}

		apply(proveedor, request, false);
		return toDto(proveedorRepository.save(proveedor));
	}

	@Transactional
	public ProveedorDto cambiarEstado(String id, boolean active) {
		Proveedor proveedor = proveedorRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
		proveedor.setActivo(active);
		return toDto(proveedorRepository.save(proveedor));
	}

	private void apply(Proveedor proveedor, ProveedorRequest request, boolean creating) {
		proveedor.setTelefono(normalizeRequired(request.phone(), "Telefono requerido"));
		proveedor.setNombre(normalizeRequired(request.name(), "Nombre requerido"));

		if (creating) {
			proveedor.setActivo(request.active() == null ? true : request.active());
		} else if (request.active() != null) {
			proveedor.setActivo(request.active());
		}
	}

	private ProveedorDto toDto(Proveedor proveedor) {
		return new ProveedorDto(
			normalizeText(proveedor.getTelefono()),
			normalizeText(proveedor.getNombre()),
			Boolean.TRUE.equals(proveedor.getActivo())
		);
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
