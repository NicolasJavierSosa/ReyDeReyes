package com.example.demo.controlador;

import com.example.demo.dto.VentaCheckoutRequest;
import com.example.demo.dto.VentaCheckoutResponse;
import com.example.demo.servicio.VentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ventas")
public class VentaApiController {

	private final VentaService ventaService;

	public VentaApiController(VentaService ventaService) {
		this.ventaService = ventaService;
	}

	@PostMapping("/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	public VentaCheckoutResponse checkout(@Valid @RequestBody VentaCheckoutRequest request) {
		return ventaService.checkout(request);
	}
}
