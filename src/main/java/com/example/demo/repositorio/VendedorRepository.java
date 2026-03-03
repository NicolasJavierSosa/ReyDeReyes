package com.example.demo.repositorio;

import com.example.demo.modelo.Vendedor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendedorRepository extends JpaRepository<Vendedor, Long> {
	Optional<Vendedor> findFirstByActivoTrueOrderByIdAsc();
}
