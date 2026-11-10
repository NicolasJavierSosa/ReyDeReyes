package com.example.demo.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.UnidadMedida;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, String> {
}
