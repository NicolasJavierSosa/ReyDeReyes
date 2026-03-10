package com.example.demo.repositorio;

import com.example.demo.modelo.Proveedor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepository extends JpaRepository<Proveedor, String> {
	List<Proveedor> findAllByOrderByNombreAsc();
	List<Proveedor> findByActivoTrueOrderByNombreAsc();
	List<Proveedor> findByActivoFalseOrderByNombreAsc();
}
