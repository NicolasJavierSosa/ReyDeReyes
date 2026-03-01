package com.example.demo.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EstadisticasController {

	@GetMapping("/estadisticas")
	public String estadisticas() {
		return "estadisticas";
	}
}
