package com.example.demo.repositorio;

import com.example.demo.modelo.Categoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
	List<Categoria> findAllByActivaTrueOrderByNombreAsc();
	boolean existsByNombreIgnoreCase(String nombre);
}
