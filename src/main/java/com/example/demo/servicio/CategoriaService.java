package com.example.demo.servicio;

import com.example.demo.dto.CategoriaRequest;
import com.example.demo.modelo.Categoria;
import com.example.demo.repositorio.CategoriaRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoriaService {

	private final CategoriaRepository categoriaRepository;

	public CategoriaService(CategoriaRepository categoriaRepository) {
		this.categoriaRepository = categoriaRepository;
	}

	public List<Categoria> listarActivas() {
		return categoriaRepository.findAllByActivaTrueOrderByNombreAsc();
	}

	public Categoria crear(CategoriaRequest request) {
		String nombre = normalizeNombre(request.nombre());
		if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria existente");
		}

		Categoria categoria = new Categoria();
		categoria.setNombre(nombre);
		categoria.setActiva(true);

		return categoriaRepository.save(categoria);
	}

	public Categoria actualizar(Long id, CategoriaRequest request) {
		Categoria categoria = categoriaRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no encontrada"));

		String nombre = normalizeNombre(request.nombre());
		if (!categoria.getNombre().equalsIgnoreCase(nombre) && categoriaRepository.existsByNombreIgnoreCase(nombre)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria existente");
		}

		categoria.setNombre(nombre);

		return categoriaRepository.save(categoria);
	}

	public void eliminar(Long id) {
		Categoria categoria = categoriaRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no encontrada"));
		categoria.setActiva(false);
		categoriaRepository.save(categoria);
	}

	private String normalizeNombre(String nombre) {
		if (nombre == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre requerido");
		}
		return nombre.trim().replaceAll("\\s+", " ");
	}

}
