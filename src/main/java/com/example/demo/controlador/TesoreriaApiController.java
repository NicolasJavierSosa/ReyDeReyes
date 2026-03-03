package com.example.demo.controlador;

import com.example.demo.dto.MovimientoManualRequest;
import com.example.demo.dto.MovimientoTesoreriaDto;
import com.example.demo.dto.TesoreriaResumenDto;
import com.example.demo.servicio.TesoreriaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
		tesoreriaService.eliminarMovimientoManual(id);
	}
}
