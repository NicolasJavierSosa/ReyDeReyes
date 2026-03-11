package com.example.demo.repositorio;

import com.example.demo.enums.EstadoSolicitudCompra;
import com.example.demo.modelo.SolicitudCompra;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudCompraRepository extends JpaRepository<SolicitudCompra, Long> {

	@Query("""
		select distinct s
		from SolicitudCompra s
		left join fetch s.items i
		left join fetch i.producto p
		left join fetch s.proveedor prov
		where s.id = :id
		""")
	Optional<SolicitudCompra> findByIdWithItems(@Param("id") Long id);

	@Query("""
		select distinct s
		from SolicitudCompra s
		left join fetch s.items i
		left join fetch i.producto p
		left join fetch s.proveedor prov
		order by s.fechaHora desc
		""")
	List<SolicitudCompra> findWithItems();

	@Query("""
		select distinct s
		from SolicitudCompra s
		left join fetch s.items i
		left join fetch i.producto p
		left join fetch s.proveedor prov
		where s.estado in :estados
		order by s.fechaHora desc
		""")
	List<SolicitudCompra> findWithItemsByEstadoIn(@Param("estados") Collection<EstadoSolicitudCompra> estados);
}
