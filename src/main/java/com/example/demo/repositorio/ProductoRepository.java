package com.example.demo.repositorio;

import com.example.demo.modelo.Producto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
	List<Producto> findAllByOrderByNombreAsc();
	Optional<Producto> findByCodigoBarra(String codigoBarra);
	Optional<Producto> findByCodigoBarraAndActivoTrue(String codigoBarra);
	boolean existsByCodigoBarra(String codigoBarra);
	boolean existsByCodigoBarraAndIdNot(String codigoBarra, Long id);
	List<Producto> findTop20ByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);
	List<Producto> findByCategoriaIdAndActivoTrue(Long categoriaId);
}
