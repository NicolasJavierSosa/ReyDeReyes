package com.example.demo.repositorio;

import com.example.demo.modelo.UnidadMedida;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Long> {
	Optional<UnidadMedida> findByAbreviaturaIgnoreCase(String abreviatura);
}
