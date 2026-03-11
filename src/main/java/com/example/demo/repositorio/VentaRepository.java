package com.example.demo.repositorio;

import com.example.demo.enums.EstadoVenta;
import com.example.demo.modelo.Venta;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long> {

	@Query("""
		select distinct v
		from Venta v
		left join fetch v.detalles d
		left join fetch d.producto p
		left join fetch p.categoria c
		where v.estado = :estado
		  and v.fechaHora between :start and :end
		""")
	List<Venta> findWithDetailsByEstadoAndFechaHoraBetween(
		@Param("estado") EstadoVenta estado,
		@Param("start") LocalDateTime start,
		@Param("end") LocalDateTime end
	);
}
