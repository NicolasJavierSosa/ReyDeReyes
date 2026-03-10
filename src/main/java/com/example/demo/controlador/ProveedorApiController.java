package com.example.demo.controlador;

import com.example.demo.dto.ProveedorDto;
import com.example.demo.dto.ProveedorEstadoRequest;
import com.example.demo.dto.ProveedorRequest;
import com.example.demo.servicio.ProveedorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorApiController {

	private final ProveedorService proveedorService;

	public ProveedorApiController(ProveedorService proveedorService) {
		this.proveedorService = proveedorService;
	}

	@GetMapping
	public List<ProveedorDto> listar(@RequestParam(defaultValue = "all") String status) {
		return proveedorService.listar(status);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProveedorDto crear(@Valid @RequestBody ProveedorRequest request) {
		return proveedorService.crear(request);
	}

	@PutMapping("/{id}")
	public ProveedorDto actualizar(@PathVariable String id, @Valid @RequestBody ProveedorRequest request) {
		return proveedorService.actualizar(id, request);
	}

	@PatchMapping("/{id}/estado")
	public ProveedorDto cambiarEstado(@PathVariable String id, @Valid @RequestBody ProveedorEstadoRequest request) {
		return proveedorService.cambiarEstado(id, request.active());
	}
}
