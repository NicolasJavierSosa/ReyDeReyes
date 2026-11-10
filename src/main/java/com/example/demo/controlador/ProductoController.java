package com.example.demo.controlador;

import java.util.UUID;
import com.example.demo.dto.AjusteStockRequest;
import com.example.demo.dto.ComboRequest;
import com.example.demo.dto.MovimientoStockDto;
import com.example.demo.dto.ProductoDto;
import com.example.demo.dto.ProductoEstadoRequest;
import com.example.demo.dto.ProductoRequest;
import com.example.demo.dto.ProductoVentaDto;
import com.example.demo.servicio.ProductoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

	private final ProductoService productoService;

	public ProductoController(ProductoService productoService) {
		this.productoService = productoService;
	}

	@GetMapping
	public List<ProductoDto> listar() {
		return productoService.listar();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductoDto crear(@Valid @RequestBody ProductoRequest request) {
		return productoService.crear(request);
	}

	@PostMapping("/combos")
	@ResponseStatus(HttpStatus.CREATED)
	public ProductoDto crearCombo(@Valid @RequestBody ComboRequest request) {
		return productoService.crearCombo(request);
	}

	@PutMapping("/{id}")
	public ProductoDto actualizar(@PathVariable UUID id, @Valid @RequestBody ProductoRequest request) {
		return productoService.actualizar(id, request);
	}

	@PutMapping("/combos/{id}")
	public ProductoDto actualizarCombo(@PathVariable UUID id, @Valid @RequestBody ComboRequest request) {
		return productoService.actualizarCombo(id, request);
	}

	@PatchMapping("/{id}/estado")
	public ProductoDto cambiarEstado(@PathVariable UUID id, @Valid @RequestBody ProductoEstadoRequest request) {
		return productoService.cambiarEstado(id, request.active());
	}

	@PostMapping("/{id}/ajuste-stock")
	public ProductoDto ajustarStock(@PathVariable UUID id, @Valid @RequestBody AjusteStockRequest request) {
		return productoService.ajustarStock(id, request);
	}

	@GetMapping("/{id}/movimientos")
	public List<MovimientoStockDto> movimientos(@PathVariable UUID id) {
		return productoService.movimientos(id);
	}

	@GetMapping("/venta/codigo/{barcode}")
	public ProductoVentaDto obtenerParaVenta(@PathVariable String barcode) {
		return productoService.obtenerParaVentaPorCodigo(barcode);
	}

	@GetMapping("/venta/buscar")
	public List<ProductoVentaDto> buscarParaVenta(
		@RequestParam("q") String query,
		@RequestParam(value = "categoriaId", required = false) UUID categoriaId
	) {
		return productoService.buscarParaVentaPorNombre(query, categoriaId);
	}
}
