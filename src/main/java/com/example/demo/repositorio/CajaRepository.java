package com.example.demo.repositorio;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.enums.EstadoCaja;
import com.example.demo.modelo.Caja;

public interface CajaRepository extends JpaRepository<Caja, UUID> {

	/** La caja abierta de un origen: LOCAL (la PC del local) o PROD (la web). Hay una por origen. */
	Optional<Caja> findFirstByEstadoAndOrigen(EstadoCaja estado, String origen);

	/** La caja abierta del local; la de la web nunca se maneja desde aca. */
	default Optional<Caja> cajaAbiertaLocal() {
		return findFirstByEstadoAndOrigen(EstadoCaja.ABIERTA, "LOCAL");
	}

	@Query("""
		select c from Caja c
		where c.estado = :estado
		  and (cast(:desde as timestamp) is null or c.fechaCierre >= :desde)
		  and (cast(:hasta as timestamp) is null or c.fechaCierre < :hasta)
		order by c.fechaCierre desc
		""")
	Page<Caja> buscarCerradas(
		@Param("estado") EstadoCaja estado,
		@Param("desde") LocalDateTime desde,
		@Param("hasta") LocalDateTime hasta,
		Pageable pageable
	);
}
