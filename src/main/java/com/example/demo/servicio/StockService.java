package com.example.demo.servicio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.enums.TipoMovimientoStock;
import com.example.demo.modelo.MovimientoStock;
import com.example.demo.modelo.Producto;
import com.example.demo.modelo.SolicitudCompra;
import com.example.demo.modelo.Venta;
import com.example.demo.repositorio.MovimientoStockRepository;
import com.example.demo.util.Valores;

/**
 * El stock nunca se guarda como un numero que se pisa: cada entrada o salida es un
 * movimiento y la cantidad actual es la suma (vista v_stock). Asi una venta local y
 * un ajuste hecho en otro momento se suman sin perderse.
 */
@Service
public class StockService {

	private final NamedParameterJdbcTemplate jdbc;
	private final MovimientoStockRepository movimientoStockRepository;

	public StockService(NamedParameterJdbcTemplate jdbc, MovimientoStockRepository movimientoStockRepository) {
		this.jdbc = jdbc;
		this.movimientoStockRepository = movimientoStockRepository;
	}

	@Transactional(readOnly = true)
	public Map<UUID, BigDecimal> stockDeTodos() {
		Map<UUID, BigDecimal> result = new HashMap<>();
		jdbc.query("select producto_id, cantidad from v_stock", rs -> {
			result.put(rs.getObject("producto_id", UUID.class), rs.getBigDecimal("cantidad"));
		});
		return result;
	}

	@Transactional(readOnly = true)
	public Map<UUID, BigDecimal> stockDe(Collection<UUID> productoIds) {
		Map<UUID, BigDecimal> result = new HashMap<>();
		if (productoIds == null || productoIds.isEmpty()) {
			return result;
		}
		jdbc.query(
			"select producto_id, cantidad from v_stock where producto_id in (:ids)",
			new MapSqlParameterSource("ids", productoIds),
			rs -> {
				result.put(rs.getObject("producto_id", UUID.class), rs.getBigDecimal("cantidad"));
			}
		);
		return result;
	}

	@Transactional(readOnly = true)
	public BigDecimal stockDe(UUID productoId) {
		return Valores.cero(stockDe(List.of(productoId)).get(productoId));
	}

	/**
	 * Bloquea las filas de los productos hasta el fin de la transaccion, para que dos
	 * operaciones simultaneas no vendan el mismo stock. Se bloquean siempre en el mismo
	 * orden para evitar bloqueos cruzados.
	 */
	@Transactional
	public void bloquear(Collection<UUID> productoIds) {
		if (productoIds == null || productoIds.isEmpty()) {
			return;
		}
		jdbc.queryForList(
			"select id from productos where id in (:ids) order by id for update",
			new MapSqlParameterSource("ids", productoIds),
			UUID.class
		);
	}

	@Transactional
	public MovimientoStock registrar(
		Producto producto,
		TipoMovimientoStock tipo,
		BigDecimal cantidad,
		String motivo,
		Venta venta,
		SolicitudCompra solicitud
	) {
		MovimientoStock movimiento = new MovimientoStock();
		movimiento.setProducto(producto);
		movimiento.setTipo(tipo);
		movimiento.setCantidad(Valores.cantidad(cantidad));
		movimiento.setMotivo(Valores.textoONull(motivo));
		movimiento.setFechaHora(LocalDateTime.now());
		movimiento.setVenta(venta);
		movimiento.setSolicitudCompra(solicitud);
		// flush: el stock se lee por JDBC (vista v_stock) y tiene que ver este movimiento
		return movimientoStockRepository.saveAndFlush(movimiento);
	}

	@Transactional(readOnly = true)
	public List<MovimientoStock> movimientosDe(UUID productoId) {
		return movimientoStockRepository.findAllByProductoIdOrderByFechaHoraDesc(productoId);
	}
}
