package com.example.demo.repositorio;

import com.example.demo.modelo.Configuracion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {
	Optional<Configuracion> findByClave(String clave);
}
