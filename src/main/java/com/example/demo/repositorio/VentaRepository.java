package com.example.demo.repositorio;

import com.example.demo.modelo.Venta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaRepository extends JpaRepository<Venta, Long> {}
