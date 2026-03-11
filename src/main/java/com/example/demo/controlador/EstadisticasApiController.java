package com.example.demo.controlador;

import com.example.demo.dto.EstadisticasResponse;
import com.example.demo.servicio.EstadisticasService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/estadisticas")
public class EstadisticasApiController {

	private final EstadisticasService estadisticasService;

	public EstadisticasApiController(EstadisticasService estadisticasService) {
		this.estadisticasService = estadisticasService;
	}

	@GetMapping
	public EstadisticasResponse obtener(@RequestParam(defaultValue = "semana") String period) {
		return estadisticasService.obtener(period);
	}
}
