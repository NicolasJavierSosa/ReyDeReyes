package com.example.demo.repositorio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.MovimientoStock;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, UUID> {
	List<MovimientoStock> findAllByProductoIdOrderByFechaHoraDesc(UUID productoId);
	List<MovimientoStock> findAllByVentaId(UUID ventaId);
}
