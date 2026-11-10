package com.example.demo.controlador;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * La interfaz es una aplicacion Vue (src/main/resources/static). Cualquier ruta que no
 * sea de la API ni un archivo se responde con index.html y Vue Router muestra la pantalla.
 */
@Controller
public class SpaController {

	@GetMapping({"/", "/{ruta:^(?!api$|assets$)[^.]*}", "/{ruta:^(?!api$|assets$)[^.]*}/**"})
	public String spa() {
		return "forward:/index.html";
	}
}
