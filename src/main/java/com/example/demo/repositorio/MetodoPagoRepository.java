package com.example.demo.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.MetodoPago;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, UUID> {
	List<MetodoPago> findAllByOrderByOrdenAscNombreAsc();
	List<MetodoPago> findByActivoTrueOrderByOrdenAscNombreAsc();
	Optional<MetodoPago> findByCodigoIgnoreCase(String codigo);
	boolean existsByCodigoIgnoreCase(String codigo);
}
