package com.example.demo.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.modelo.Tipo;

public interface TipoRepository extends JpaRepository<Tipo, String> {
}
