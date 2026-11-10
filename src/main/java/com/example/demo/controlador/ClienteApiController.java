package com.example.demo.controlador;

import java.util.List;
import java.util.UUID;

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

import com.example.demo.dto.ClienteDto;
import com.example.demo.dto.ClienteEstadoRequest;
import com.example.demo.dto.ClienteRequest;
import com.example.demo.servicio.ClienteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clientes")
public class ClienteApiController {

	private final ClienteService clienteService;

	public ClienteApiController(ClienteService clienteService) {
		this.clienteService = clienteService;
	}

	@GetMapping
	public List<ClienteDto> listar(@RequestParam(defaultValue = "all") String status) {
		return clienteService.listar(status);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ClienteDto crear(@Valid @RequestBody ClienteRequest request) {
		return clienteService.crear(request);
	}

	@PutMapping("/{id}")
	public ClienteDto actualizar(@PathVariable UUID id, @Valid @RequestBody ClienteRequest request) {
		return clienteService.actualizar(id, request);
	}

	@PatchMapping("/{id}/estado")
	public ClienteDto cambiarEstado(@PathVariable UUID id, @Valid @RequestBody ClienteEstadoRequest request) {
		return clienteService.cambiarEstado(id, request.active());
	}
}
