package com.example.demo.controlador;

import com.example.demo.dto.OpenFoodFactsProductDto;
import com.example.demo.servicio.OpenFoodFactsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/openfoodfacts")
public class OpenFoodFactsController {

	private final OpenFoodFactsService openFoodFactsService;

	public OpenFoodFactsController(OpenFoodFactsService openFoodFactsService) {
		this.openFoodFactsService = openFoodFactsService;
	}

	@GetMapping("/{barcode}")
	public OpenFoodFactsProductDto lookup(@PathVariable String barcode) {
		return openFoodFactsService.lookup(barcode);
	}
}
