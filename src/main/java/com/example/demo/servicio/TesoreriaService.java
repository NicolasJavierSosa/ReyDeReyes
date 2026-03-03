package com.example.demo.servicio;

import com.example.demo.dto.MovimientoManualRequest;
import com.example.demo.dto.MovimientoTesoreriaDto;
import com.example.demo.dto.TesoreriaResumenDto;
import com.example.demo.enums.MetodoPago;
import com.example.demo.enums.TipoMovimientoTesoreria;
import com.example.demo.modelo.MovimientoTesoreria;
import com.example.demo.modelo.Venta;
import com.example.demo.repositorio.MovimientoTesoreriaRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TesoreriaService {

	private final MovimientoTesoreriaRepository movimientoTesoreriaRepository;

	public TesoreriaService(MovimientoTesoreriaRepository movimientoTesoreriaRepository) {
		this.movimientoTesoreriaRepository = movimientoTesoreriaRepository;
	}

	@Transactional(readOnly = true)
	public List<MovimientoTesoreriaDto> listarMovimientos() {
		return movimientoTesoreriaRepository.findAllByOrderByFechaHoraDescIdDesc().stream()
			.map(this::toDto)
			.toList();
	}

	@Transactional(readOnly = true)
	public TesoreriaResumenDto obtenerResumen() {
		BigDecimal totalIngresos = BigDecimal.ZERO;
		BigDecimal totalVentas = BigDecimal.ZERO;
		BigDecimal totalEgresos = BigDecimal.ZERO;
		BigDecimal efectivoCaja = BigDecimal.ZERO;

		for (MovimientoTesoreria movimiento : movimientoTesoreriaRepository.findAll()) {
			if (movimiento.getTipo() == TipoMovimientoTesoreria.INGRESO) {
				totalIngresos = totalIngresos.add(absOrZero(movimiento.getMonto()));
			} else if (movimiento.getTipo() == TipoMovimientoTesoreria.VENTA) {
				totalVentas = totalVentas.add(absOrZero(movimiento.getMonto()));
			} else if (movimiento.getTipo() == TipoMovimientoTesoreria.EGRESO) {
				totalEgresos = totalEgresos.add(absOrZero(movimiento.getMonto()));
			}

			if (Boolean.TRUE.equals(movimiento.getAfectaCaja())) {
				efectivoCaja = efectivoCaja.add(defaultZero(movimiento.getMonto()));
			}
		}

		return new TesoreriaResumenDto(totalIngresos, totalVentas, totalEgresos, efectivoCaja);
	}

	@Transactional
	public MovimientoTesoreriaDto registrarMovimientoManual(MovimientoManualRequest request) {
		if (request.type() == TipoMovimientoTesoreria.VENTA) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo VENTA es solo del sistema");
		}

		BigDecimal amount = defaultZero(request.amount());
		if (amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monto invalido");
		}

		BigDecimal signedAmount = request.type() == TipoMovimientoTesoreria.EGRESO
			? amount.negate()
			: amount;

		MovimientoTesoreria saved = movimientoTesoreriaRepository.save(MovimientoTesoreria.builder()
			.fechaHora(LocalDateTime.now())
			.tipo(request.type())
			.concepto(normalizeRequired(request.concept(), "Concepto requerido"))
			.detalle(normalizeOptional(request.detail()))
			.monto(signedAmount)
			.afectaCaja(Boolean.TRUE)
			.usuario("Admin")
			.build());

		return toDto(saved);
	}

	@Transactional
	public void eliminarMovimientoManual(Long id) {
		MovimientoTesoreria movimiento = movimientoTesoreriaRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));

		if (movimiento.getTipo() == TipoMovimientoTesoreria.VENTA) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las ventas no se eliminan desde tesoreria");
		}

		movimientoTesoreriaRepository.delete(movimiento);
	}

	@Transactional
	public void registrarVenta(Venta venta, MetodoPago metodoPago, BigDecimal total, boolean afectaCaja, String usuario) {
		String ticket = "Ticket #" + String.format("%06d", venta.getId());
		String concepto = metodoPago == MetodoPago.TRANSFERENCIA ? "Venta por transferencia" : "Venta en efectivo";

		movimientoTesoreriaRepository.save(MovimientoTesoreria.builder()
			.fechaHora(LocalDateTime.now())
			.tipo(TipoMovimientoTesoreria.VENTA)
			.concepto(concepto)
			.detalle(ticket)
			.monto(defaultZero(total))
			.afectaCaja(afectaCaja)
			.metodoPago(metodoPago)
			.usuario(normalizeOptional(usuario))
			.venta(venta)
			.build());
	}

	private MovimientoTesoreriaDto toDto(MovimientoTesoreria movimiento) {
		return new MovimientoTesoreriaDto(
			movimiento.getId(),
			movimiento.getFechaHora(),
			movimiento.getTipo(),
			movimiento.getConcepto(),
			movimiento.getDetalle(),
			movimiento.getUsuario(),
			defaultZero(movimiento.getMonto()),
			movimiento.getMetodoPago(),
			movimiento.getVenta() != null ? movimiento.getVenta().getId() : null,
			Boolean.TRUE.equals(movimiento.getAfectaCaja()),
			movimiento.getTipo() != TipoMovimientoTesoreria.VENTA
		);
	}

	private String normalizeRequired(String value, String message) {
		String normalized = normalizeOptional(value);
		if (normalized.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		}
		return normalized;
	}

	private String normalizeOptional(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private BigDecimal absOrZero(BigDecimal value) {
		return defaultZero(value).abs();
	}
}
