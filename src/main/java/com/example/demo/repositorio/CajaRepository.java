package com.example.demo.repositorio;

import com.example.demo.enums.EstadoCaja;
import com.example.demo.modelo.Caja;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CajaRepository extends JpaRepository<Caja, Long> {
	Optional<Caja> findFirstByEstadoOrderByFechaAperturaDesc(EstadoCaja estado);

	Page<Caja> findByEstadoOrderByFechaCierreDesc(EstadoCaja estado, Pageable pageable);

	Page<Caja> findByEstadoAndFechaCierreGreaterThanEqualOrderByFechaCierreDesc(
		EstadoCaja estado,
		LocalDateTime from,
		Pageable pageable
	);

	Page<Caja> findByEstadoAndFechaCierreLessThanOrderByFechaCierreDesc(
		EstadoCaja estado,
		LocalDateTime toExclusive,
		Pageable pageable
	);

	Page<Caja> findByEstadoAndFechaCierreBetweenOrderByFechaCierreDesc(
		EstadoCaja estado,
		LocalDateTime from,
		LocalDateTime toExclusive,
		Pageable pageable
	);
}
