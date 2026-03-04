package com.example.demo.controlador;

import com.example.demo.dto.AperturaCajaRequest;
import com.example.demo.dto.AperturaCajaResponse;
import com.example.demo.dto.CajasCerradasResponse;
import com.example.demo.dto.CierreCajaRequest;
import com.example.demo.dto.CierreCajaResponse;
import com.example.demo.dto.EstadoCajaDto;
import com.example.demo.dto.MovimientoManualRequest;
import com.example.demo.dto.MovimientoTesoreriaDto;
import com.example.demo.dto.TesoreriaResumenDto;
import com.example.demo.servicio.TesoreriaService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tesoreria")
public class TesoreriaApiController {

	private final TesoreriaService tesoreriaService;

	public TesoreriaApiController(TesoreriaService tesoreriaService) {
		this.tesoreriaService = tesoreriaService;
	}

	@GetMapping("/movimientos")
	public List<MovimientoTesoreriaDto> listarMovimientos() {
		return tesoreriaService.listarMovimientos();
	}

	@GetMapping("/cajas/{id}/movimientos")
	public List<MovimientoTesoreriaDto> listarMovimientosPorCaja(
		@PathVariable Long id,
		@RequestParam(required = false) String type,
		@RequestParam(required = false) String status,
		@RequestParam(required = false) String search
	) {
		return tesoreriaService.listarMovimientosPorCaja(id, type, status, search);
	}

	@GetMapping("/resumen")
	public TesoreriaResumenDto resumen() {
		return tesoreriaService.obtenerResumen();
	}

	@PostMapping("/movimientos/manual")
	@ResponseStatus(HttpStatus.CREATED)
	public MovimientoTesoreriaDto registrarManual(@Valid @RequestBody MovimientoManualRequest request) {
		return tesoreriaService.registrarMovimientoManual(request);
	}

	@DeleteMapping("/movimientos/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarManual(@PathVariable Long id) {
		tesoreriaService.anularMovimientoManual(id);
	}

	@PatchMapping("/movimientos/{id}/anular")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void anularManual(@PathVariable Long id) {
		tesoreriaService.anularMovimientoManual(id);
	}

	@PostMapping("/caja/cierre")
	public CierreCajaResponse cerrarCaja(@Valid @RequestBody CierreCajaRequest request) {
		return tesoreriaService.cerrarCaja(request);
	}

	@GetMapping("/caja/estado")
	public EstadoCajaDto estadoCaja() {
		return tesoreriaService.obtenerEstadoCaja();
	}

	@PostMapping("/caja/apertura")
	@ResponseStatus(HttpStatus.CREATED)
	public AperturaCajaResponse abrirCaja(@Valid @RequestBody AperturaCajaRequest request) {
		return tesoreriaService.abrirCaja(request);
	}

	@GetMapping("/cajas/cerradas")
	public CajasCerradasResponse listarCerradas(
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "10") int size
	) {
		return tesoreriaService.listarCajasCerradas(from, to, page, size);
	}
}
