package com.example.demo.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.Configuracion;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, String> {
}
