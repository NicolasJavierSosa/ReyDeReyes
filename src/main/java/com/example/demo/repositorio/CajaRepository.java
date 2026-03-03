package com.example.demo.repositorio;

import com.example.demo.enums.EstadoCaja;
import com.example.demo.modelo.Caja;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CajaRepository extends JpaRepository<Caja, Long> {
	Optional<Caja> findFirstByEstadoOrderByFechaAperturaDesc(EstadoCaja estado);
}
