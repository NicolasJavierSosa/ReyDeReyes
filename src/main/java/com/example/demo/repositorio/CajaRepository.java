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

	Optional<Caja> findFirstByEstado(EstadoCaja estado);

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
