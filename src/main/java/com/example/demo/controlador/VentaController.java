package com.example.demo.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VentaController {

	@GetMapping("/")
	public String root() {
		return "redirect:/venta";
	}

	@GetMapping("/venta")
	public String venta() {
		return "venta";
	}
}
