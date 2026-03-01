package com.example.demo.repositorio;

import com.example.demo.modelo.Tipo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoRepository extends JpaRepository<Tipo, Long> {
	Optional<Tipo> findByNombreIgnoreCase(String nombre);
}
