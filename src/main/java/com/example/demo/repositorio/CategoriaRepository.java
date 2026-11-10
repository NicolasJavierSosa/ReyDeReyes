package com.example.demo.repositorio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {
	List<Categoria> findAllByActivaTrueOrderByNombreAsc();
	List<Categoria> findAllByActivaOrderByNombreAsc(Boolean activa);
	List<Categoria> findAllByOrderByNombreAsc();
	boolean existsByNombreIgnoreCase(String nombre);
}
