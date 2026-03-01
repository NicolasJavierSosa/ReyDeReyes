package com.example.demo.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TesoreriaController {

	@GetMapping("/tesoreria")
	public String tesoreria() {
		return "tesoreria";
	}
}
