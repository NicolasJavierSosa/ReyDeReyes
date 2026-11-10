package com.example.demo.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, String> {
	List<Proveedor> findAllByOrderByNombreAsc();
	List<Proveedor> findByActivoOrderByNombreAsc(Boolean activo);
}
