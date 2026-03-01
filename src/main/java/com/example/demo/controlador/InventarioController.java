package com.example.demo.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InventarioController {

	@GetMapping("/inventario")
	public String inventario() {
		return "inventario";
	}
}
