package com.example.demo.repositorio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.MovimientoTesoreria;

public interface MovimientoTesoreriaRepository extends JpaRepository<MovimientoTesoreria, UUID> {
	List<MovimientoTesoreria> findAllByVentaId(UUID ventaId);
	List<MovimientoTesoreria> findAllByCajaIdOrderByFechaHoraDescIdDesc(UUID cajaId);
}
