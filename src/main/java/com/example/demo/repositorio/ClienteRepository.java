package com.example.demo.repositorio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
	List<Cliente> findAllByOrderByApellidoAscNombreAsc();
	List<Cliente> findByActivoOrderByApellidoAscNombreAsc(Boolean activo);
	boolean existsByEmailIgnoreCase(String email);
	boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
}
