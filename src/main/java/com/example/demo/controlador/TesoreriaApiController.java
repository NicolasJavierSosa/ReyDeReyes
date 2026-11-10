package com.example.demo.controlador;

import java.util.UUID;
import com.example.demo.dto.AperturaCajaRequest;
import com.example.demo.dto.AperturaCajaResponse;
import com.example.demo.dto.CajasCerradasResponse;
import com.example.demo.dto.CierreCajaRequest;
import com.example.demo.dto.CierreCajaResponse;
import com.example.demo.dto.EstadoCajaDto;
import com.example.demo.dto.MovimientoManualRequest;
import com.example.demo.dto.MovimientoTesoreriaDto;
import com.example.demo.dto.TesoreriaResumenDto;
import com.example.demo.dto.SyncResultadoDto;
import com.example.demo.respaldo.RespaldoService;
import com.example.demo.servicio.TesoreriaService;
import com.example.demo.sync.SyncService;
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
	private final SyncService syncService;
	private final RespaldoService respaldoService;

	public TesoreriaApiController(TesoreriaService tesoreriaService, SyncService syncService, RespaldoService respaldoService) {
		this.tesoreriaService = tesoreriaService;
		this.syncService = syncService;
		this.respaldoService = respaldoService;
	}

	@GetMapping("/movimientos")
	public List<MovimientoTesoreriaDto> listarMovimientos() {
		return tesoreriaService.listarMovimientos();
	}

	@GetMapping("/cajas/{id}/movimientos")
	public List<MovimientoTesoreriaDto> listarMovimientosPorCaja(
		@PathVariable UUID id,
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

	/** Compatibilidad con la pantalla actual: "eliminar" en realidad anula (queda registro). */
	@DeleteMapping("/movimientos/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminarManual(@PathVariable UUID id) {
		tesoreriaService.anularMovimientoManual(id);
	}

	@PatchMapping("/movimientos/{id}/anular")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void anularManual(@PathVariable UUID id) {
		tesoreriaService.anularMovimientoManual(id);
	}

	@PostMapping("/caja/cierre")
	public CierreCajaResponse cerrarCaja(@Valid @RequestBody CierreCajaRequest request) {
		CierreCajaResponse cierre = tesoreriaService.cerrarCaja(request);
		// Al cerrar la caja: copia diaria local + sincronizacion obligatoria con la nube.
		// Si no hay internet la caja queda cerrada igual y el indicador muestra lo pendiente.
		respaldoService.copiaLocal();
		SyncResultadoDto sync = syncService.sincronizar();
		boolean subido = sync.ok() && syncService.pendientes() == 0;
		return new CierreCajaResponse(
			cierre.expectedCash(),
			cierre.countedCash(),
			cierre.difference(),
			cierre.closedAt(),
			subido,
			subido ? "Datos subidos a la nube" : sync.message()
		);
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
