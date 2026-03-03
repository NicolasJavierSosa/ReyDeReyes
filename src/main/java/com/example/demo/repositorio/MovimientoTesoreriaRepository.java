package com.example.demo.repositorio;

import com.example.demo.modelo.MovimientoTesoreria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoTesoreriaRepository extends JpaRepository<MovimientoTesoreria, Long> {
	List<MovimientoTesoreria> findAllByOrderByFechaHoraDescIdDesc();
}
