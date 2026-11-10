package com.example.demo.repositorio;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.enums.EstadoVenta;
import com.example.demo.modelo.Venta;

public interface VentaRepository extends JpaRepository<Venta, UUID> {

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

	@Query("""
		select distinct v
		from Venta v
		left join fetch v.pagos p
		where v.fechaHora >= :desde and v.fechaHora < :hasta
		order by v.fechaHora desc
		""")
	List<Venta> findByFechaHoraEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

	@Query(value = "select nextval('ventas_numero_seq')", nativeQuery = true)
	Long siguienteNumero();
}
