package com.example.demo.servicio;

import static com.example.demo.util.Valores.cero;
import static com.example.demo.util.Valores.dinero;
import static com.example.demo.util.Valores.error;
import static com.example.demo.util.Valores.noEncontrado;
import static com.example.demo.util.Valores.requerido;
import static com.example.demo.util.Valores.texto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.AperturaCajaRequest;
import com.example.demo.dto.AperturaCajaResponse;
import com.example.demo.dto.CajaCerradaDto;
import com.example.demo.dto.CajasCerradasResponse;
import com.example.demo.dto.CierreCajaRequest;
import com.example.demo.dto.CierreCajaResponse;
import com.example.demo.dto.ConteoBilleteRequest;
import com.example.demo.dto.EstadoCajaDto;
import com.example.demo.dto.MovimientoManualRequest;
import com.example.demo.dto.MovimientoTesoreriaDto;
import com.example.demo.dto.TesoreriaResumenDto;
import com.example.demo.enums.EstadoCaja;
import com.example.demo.enums.TipoMovimientoTesoreria;
import com.example.demo.modelo.Caja;
import com.example.demo.modelo.MetodoPago;
import com.example.demo.modelo.MovimientoTesoreria;
import com.example.demo.modelo.Venta;
import com.example.demo.repositorio.CajaRepository;
import com.example.demo.repositorio.MovimientoTesoreriaRepository;
import com.example.demo.seguridad.UsuarioActual;

/**
 * Caja y tesoreria. La caja se abre y se cierra a mano; los totales se calculan
 * siempre a partir de los movimientos (no hay totales duplicados en la caja).
 */
@Service
public class TesoreriaService {

	private final MovimientoTesoreriaRepository movimientoTesoreriaRepository;
	private final CajaRepository cajaRepository;

	public TesoreriaService(
		MovimientoTesoreriaRepository movimientoTesoreriaRepository,
		CajaRepository cajaRepository
	) {
		this.movimientoTesoreriaRepository = movimientoTesoreriaRepository;
		this.cajaRepository = cajaRepository;
	}

	// ------------------------------------------------------------------ caja

	@Transactional(readOnly = true)
	public Caja cajaAbierta() {
		return cajaRepository.findFirstByEstado(EstadoCaja.ABIERTA)
			.orElseThrow(() -> error("No hay caja abierta. Abra la caja antes de continuar."));
	}

	@Transactional(readOnly = true)
	public EstadoCajaDto obtenerEstadoCaja() {
		return cajaRepository.findFirstByEstado(EstadoCaja.ABIERTA)
			.map(caja -> new EstadoCajaDto(true, caja.getId(), caja.getFechaApertura(), cero(caja.getMontoApertura())))
			.orElseGet(() -> new EstadoCajaDto(false, null, null, BigDecimal.ZERO));
	}

	@Transactional
	public AperturaCajaResponse abrirCaja(AperturaCajaRequest request) {
		if (cajaRepository.findFirstByEstado(EstadoCaja.ABIERTA).isPresent()) {
			throw error("Ya hay una caja abierta");
		}
		BigDecimal amount = dinero(request.amount());
		if (amount.signum() < 0) {
			throw error("Monto de base invalido");
		}
		Caja caja = new Caja();
		caja.setFechaApertura(LocalDateTime.now());
		caja.setMontoApertura(amount);
		caja.setEstado(EstadoCaja.ABIERTA);
		caja.setObservaciones(texto(request.notes()));
		Caja saved = cajaRepository.save(caja);

		if (amount.signum() > 0) {
			MovimientoTesoreria base = new MovimientoTesoreria();
			base.setFechaHora(LocalDateTime.now());
			base.setTipo(TipoMovimientoTesoreria.INGRESO);
			base.setConcepto("Cambio Inicial / Base");
			base.setDetalle("Apertura de caja");
			base.setMonto(amount);
			base.setAnulado(false);
			base.setCaja(saved);
			base.setAfectaCaja(true);
			base.setUsuario(UsuarioActual.nombre());
			movimientoTesoreriaRepository.save(base);
		}
		return new AperturaCajaResponse(saved.getId(), saved.getFechaApertura(), saved.getMontoApertura());
	}

	/** Cierra la caja abierta. La sincronizacion y la copia diaria las dispara el controlador. */
	@Transactional
	public CierreCajaResponse cerrarCaja(CierreCajaRequest request) {
		Caja caja = cajaAbierta();
		BigDecimal efectivoContado = calcularConteoBilletes(request.bills());
		Totales totales = calcularTotales(caja);
		BigDecimal efectivoEsperado = dinero(totales.cashInDrawer());
		BigDecimal diferencia = dinero(efectivoContado.subtract(efectivoEsperado));

		caja.setFechaCierre(LocalDateTime.now());
		caja.setEstado(EstadoCaja.CERRADA);
		caja.setEfectivoEsperado(efectivoEsperado);
		caja.setEfectivoContado(efectivoContado);
		caja.setDiferencia(diferencia);
		caja.setDetalleCierre(buildDetalleCierre(request.bills()));
		caja.setObservaciones(texto(request.notes()));
		cajaRepository.save(caja);
		return new CierreCajaResponse(efectivoEsperado, efectivoContado, diferencia, caja.getFechaCierre(), false, null);
	}

	@Transactional(readOnly = true)
	public CajasCerradasResponse listarCajasCerradas(LocalDate from, LocalDate to, int page, int size) {
		int safeSize = size <= 0 ? 10 : Math.min(size, 50);
		int safePage = Math.max(page, 0);
		Page<Caja> result = cajaRepository.buscarCerradas(
			EstadoCaja.CERRADA,
			from != null ? from.atStartOfDay() : null,
			to != null ? to.plusDays(1).atStartOfDay() : null,
			PageRequest.of(safePage, safeSize)
		);
		List<CajaCerradaDto> items = result.getContent().stream()
			.map(caja -> new CajaCerradaDto(
				caja.getId(),
				caja.getFechaCierre(),
				cero(caja.getEfectivoEsperado()),
				cero(caja.getEfectivoContado()),
				cero(caja.getDiferencia()),
				UsuarioActual.nombre(),
				texto(caja.getObservaciones())
			))
			.toList();
		return new CajasCerradasResponse(items, safePage, safeSize, result.hasNext());
	}

	// ------------------------------------------------------------------ movimientos

	@Transactional(readOnly = true)
	public List<MovimientoTesoreriaDto> listarMovimientos() {
		return cajaRepository.findFirstByEstado(EstadoCaja.ABIERTA)
			.map(caja -> movimientoTesoreriaRepository.findAllByCajaIdOrderByFechaHoraDescIdDesc(caja.getId()).stream()
				.map(this::toDto)
				.toList())
			.orElse(List.of());
	}

	@Transactional(readOnly = true)
	public List<MovimientoTesoreriaDto> listarMovimientosPorCaja(UUID cajaId, String type, String status, String search) {
		if (cajaId == null || !cajaRepository.existsById(cajaId)) {
			throw noEncontrado("Caja no encontrada");
		}
		String searchTerm = texto(search).toLowerCase();
		String typeFilter = texto(type).toLowerCase();
		String statusFilter = texto(status).toLowerCase();
		return movimientoTesoreriaRepository.findAllByCajaIdOrderByFechaHoraDescIdDesc(cajaId).stream()
			.filter(mov -> searchTerm.isEmpty() || String.join(" ",
				texto(mov.getConcepto()), texto(mov.getDetalle()), texto(mov.getUsuario())).toLowerCase().contains(searchTerm))
			.filter(mov -> typeFilter.isEmpty() || "all".equals(typeFilter) || mov.getTipo().name().equalsIgnoreCase(typeFilter))
			.filter(mov -> {
				boolean voided = Boolean.TRUE.equals(mov.getAnulado());
				return statusFilter.isEmpty() || "all".equals(statusFilter)
					|| ("active".equals(statusFilter) && !voided)
					|| ("voided".equals(statusFilter) && voided);
			})
			.map(this::toDto)
			.toList();
	}

	@Transactional(readOnly = true)
	public TesoreriaResumenDto obtenerResumen() {
		Totales totales = cajaRepository.findFirstByEstado(EstadoCaja.ABIERTA)
			.map(this::calcularTotales)
			.orElse(new Totales(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
		return new TesoreriaResumenDto(
			totales.incomes(),
			totales.sales(),
			totales.expenses(),
			totales.cashInDrawer(),
			totales.nonCashSales()
		);
	}

	@Transactional
	public MovimientoTesoreriaDto registrarMovimientoManual(MovimientoManualRequest request) {
		if (request.type() == TipoMovimientoTesoreria.VENTA) {
			throw error("El tipo VENTA es solo del sistema");
		}
		BigDecimal amount = cero(request.amount());
		if (amount.signum() <= 0) {
			throw error("Monto invalido");
		}
		Caja caja = cajaAbierta();
		MovimientoTesoreria movimiento = new MovimientoTesoreria();
		movimiento.setFechaHora(LocalDateTime.now());
		movimiento.setTipo(request.type());
		movimiento.setConcepto(requerido(request.concept(), "Concepto requerido"));
		movimiento.setDetalle(texto(request.detail()));
		movimiento.setMonto(dinero(request.type() == TipoMovimientoTesoreria.EGRESO ? amount.negate() : amount));
		movimiento.setAnulado(false);
		movimiento.setCaja(caja);
		movimiento.setAfectaCaja(true);
		movimiento.setUsuario(UsuarioActual.nombre());
		return toDto(movimientoTesoreriaRepository.save(movimiento));
	}

	@Transactional
	public void anularMovimientoManual(UUID id) {
		MovimientoTesoreria movimiento = movimientoTesoreriaRepository.findById(id)
			.orElseThrow(() -> noEncontrado("Movimiento no encontrado"));
		if (movimiento.getTipo() == TipoMovimientoTesoreria.VENTA) {
			throw error("Las ventas se anulan desde su modulo");
		}
		if (movimiento.getCaja().getEstado() != EstadoCaja.ABIERTA) {
			throw error("El movimiento pertenece a una caja cerrada");
		}
		if (Boolean.TRUE.equals(movimiento.getAnulado())) {
			return;
		}
		movimiento.setAnulado(true);
		movimientoTesoreriaRepository.save(movimiento);
	}

	@Transactional
	public void registrarVenta(Venta venta, MetodoPago metodo, BigDecimal total) {
		MovimientoTesoreria movimiento = new MovimientoTesoreria();
		movimiento.setFechaHora(venta.getFechaHora());
		movimiento.setTipo(TipoMovimientoTesoreria.VENTA);
		movimiento.setConcepto("Venta - " + metodo.getNombre());
		movimiento.setDetalle("Ticket #" + com.example.demo.util.Valores.ticket(venta.getNumero()));
		movimiento.setMonto(dinero(total));
		movimiento.setAnulado(false);
		movimiento.setCaja(venta.getCaja());
		movimiento.setAfectaCaja(Boolean.TRUE.equals(metodo.getAfectaCaja()));
		movimiento.setMetodoPago(metodo);
		movimiento.setMetodoNombre(metodo.getNombre());
		movimiento.setUsuario(UsuarioActual.nombre());
		movimiento.setVenta(venta);
		movimientoTesoreriaRepository.save(movimiento);
	}

	@Transactional
	public void anularMovimientosVenta(UUID ventaId) {
		movimientoTesoreriaRepository.findAllByVentaId(ventaId).forEach(movimiento -> {
			if (!Boolean.TRUE.equals(movimiento.getAnulado())) {
				movimiento.setAnulado(true);
				movimientoTesoreriaRepository.save(movimiento);
			}
		});
	}

	// ------------------------------------------------------------------ auxiliares

	private MovimientoTesoreriaDto toDto(MovimientoTesoreria movimiento) {
		return new MovimientoTesoreriaDto(
			movimiento.getId(),
			movimiento.getFechaHora(),
			movimiento.getTipo(),
			movimiento.getConcepto(),
			movimiento.getDetalle(),
			movimiento.getUsuario(),
			cero(movimiento.getMonto()),
			movimiento.getMetodoNombre(),
			movimiento.getVenta() != null ? movimiento.getVenta().getId() : null,
			Boolean.TRUE.equals(movimiento.getAfectaCaja()),
			movimiento.getTipo() != TipoMovimientoTesoreria.VENTA,
			Boolean.TRUE.equals(movimiento.getAnulado())
		);
	}

	private BigDecimal calcularConteoBilletes(List<ConteoBilleteRequest> bills) {
		if (bills == null || bills.isEmpty()) {
			throw error("Debe ingresar el recuento de billetes");
		}
		BigDecimal total = BigDecimal.ZERO;
		for (ConteoBilleteRequest bill : bills) {
			if (bill == null) {
				continue;
			}
			if (bill.quantity() == null || bill.quantity() < 0) {
				throw error("Cantidad de billetes invalida");
			}
			total = total.add(cero(bill.denomination()).multiply(BigDecimal.valueOf(bill.quantity())));
		}
		return dinero(total);
	}

	private String buildDetalleCierre(List<ConteoBilleteRequest> bills) {
		StringBuilder sb = new StringBuilder("{\"billetes\":{");
		boolean first = true;
		for (ConteoBilleteRequest bill : bills) {
			if (bill == null) {
				continue;
			}
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append('"').append(cero(bill.denomination()).stripTrailingZeros().toPlainString()).append("\":")
				.append(bill.quantity() == null ? 0 : bill.quantity());
		}
		return sb.append("}}").toString();
	}

	private Totales calcularTotales(Caja caja) {
		BigDecimal ingresos = BigDecimal.ZERO;
		BigDecimal ventas = BigDecimal.ZERO;
		BigDecimal egresos = BigDecimal.ZERO;
		BigDecimal efectivo = BigDecimal.ZERO;
		BigDecimal otrosMedios = BigDecimal.ZERO;
		for (MovimientoTesoreria m : movimientoTesoreriaRepository.findAllByCajaIdOrderByFechaHoraDescIdDesc(caja.getId())) {
			if (Boolean.TRUE.equals(m.getAnulado())) {
				continue;
			}
			BigDecimal monto = cero(m.getMonto());
			switch (m.getTipo()) {
				case INGRESO -> ingresos = ingresos.add(monto.abs());
				case VENTA -> ventas = ventas.add(monto.abs());
				case EGRESO -> egresos = egresos.add(monto.abs());
			}
			if (Boolean.TRUE.equals(m.getAfectaCaja())) {
				efectivo = efectivo.add(monto);
			} else if (m.getTipo() == TipoMovimientoTesoreria.VENTA) {
				otrosMedios = otrosMedios.add(monto.abs());
			}
		}
		return new Totales(ingresos, ventas, egresos, efectivo, otrosMedios);
	}

	private record Totales(
		BigDecimal incomes,
		BigDecimal sales,
		BigDecimal expenses,
		BigDecimal cashInDrawer,
		BigDecimal nonCashSales
	) {}
}
