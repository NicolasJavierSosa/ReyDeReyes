package com.example.demo.controlador;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.MetodoPagoDto;
import com.example.demo.dto.MetodoPagoRequest;
import com.example.demo.servicio.MetodoPagoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/metodos-pago")
public class MetodoPagoController {

	private final MetodoPagoService metodoPagoService;

	public MetodoPagoController(MetodoPagoService metodoPagoService) {
		this.metodoPagoService = metodoPagoService;
	}

	@GetMapping
	public List<MetodoPagoDto> listar(@RequestParam(defaultValue = "true") boolean onlyActive) {
		return metodoPagoService.listar(onlyActive);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MetodoPagoDto crear(@Valid @RequestBody MetodoPagoRequest request) {
		return metodoPagoService.crear(request);
	}

	@PutMapping("/{id}")
	public MetodoPagoDto actualizar(@PathVariable UUID id, @Valid @RequestBody MetodoPagoRequest request) {
		return metodoPagoService.actualizar(id, request);
	}
}
