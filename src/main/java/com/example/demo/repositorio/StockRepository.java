package com.example.demo.repositorio;

import com.example.demo.modelo.Stock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
	Optional<Stock> findByProductoId(Long productoId);
}
