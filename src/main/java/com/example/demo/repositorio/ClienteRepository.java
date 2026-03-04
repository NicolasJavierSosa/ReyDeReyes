package com.example.demo.repositorio;

import com.example.demo.modelo.Cliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
	List<Cliente> findAllByOrderByNombreCompletoAsc();
	List<Cliente> findByActivoTrueOrderByNombreCompletoAsc();
	List<Cliente> findByActivoFalseOrderByNombreCompletoAsc();

	Optional<Cliente> findFirstByEmailIgnoreCase(String email);
	boolean existsByEmailIgnoreCase(String email);
	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
