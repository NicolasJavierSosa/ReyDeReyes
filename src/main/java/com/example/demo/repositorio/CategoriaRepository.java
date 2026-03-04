package com.example.demo.repositorio;

import com.example.demo.modelo.Categoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
	List<Categoria> findAllByActivaTrueOrderByNombreAsc();
	List<Categoria> findAllByActivaOrderByNombreAsc(Boolean activa);
	List<Categoria> findAllByOrderByNombreAsc();
	boolean existsByNombreIgnoreCase(String nombre);
}
