package com.example.demo.controlador;

import java.util.UUID;
import java.time.LocalDate;
import java.util.List;
import com.example.demo.dto.VentaResumenDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.demo.dto.VentaCheckoutRequest;
import com.example.demo.dto.VentaCheckoutResponse;
import com.example.demo.dto.VentaDetalleResponse;
import com.example.demo.servicio.VentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	@GetMapping
	public List<VentaResumenDto> listar(
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
	) {
		return ventaService.listar(desde, hasta);
	}

	@PatchMapping("/{id}/anular")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void anular(@PathVariable UUID id) {
		ventaService.anularVenta(id);
	}

	@GetMapping("/{id}")
	public VentaDetalleResponse detalle(@PathVariable UUID id) {
		return ventaService.obtenerDetalle(id);
	}
}
