package com.example.demo.controlador;

import com.example.demo.dto.CategoriaDto;
import com.example.demo.dto.CategoriaRequest;
import com.example.demo.modelo.Categoria;
import com.example.demo.servicio.CategoriaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

	private final CategoriaService categoriaService;

	public CategoriaController(CategoriaService categoriaService) {
		this.categoriaService = categoriaService;
	}

	@GetMapping
	public List<CategoriaDto> listar() {
		return categoriaService.listarActivas().stream()
			.map(CategoriaController::toDto)
			.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CategoriaDto crear(@Valid @RequestBody CategoriaRequest request) {
		return toDto(categoriaService.crear(request));
	}

	@PutMapping("/{id}")
	public CategoriaDto actualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
		return toDto(categoriaService.actualizar(id, request));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable Long id) {
		categoriaService.eliminar(id);
	}

	private static CategoriaDto toDto(Categoria categoria) {
		return new CategoriaDto(
			categoria.getId(),
			categoria.getNombre(),
			categoria.getActiva()
		);
	}
}
