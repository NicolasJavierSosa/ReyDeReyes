package com.example.demo.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.Producto;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {
	List<Producto> findAllByOrderByNombreAsc();
	Optional<Producto> findByCodigoBarraAndActivoTrue(String codigoBarra);
	boolean existsByCodigoBarra(String codigoBarra);
	boolean existsByCodigoBarraAndIdNot(String codigoBarra, UUID id);
	List<Producto> findTop20ByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);
	List<Producto> findByCategoriaIdAndActivoTrueAndComboFalse(UUID categoriaId);
}
