package com.example.demo.servicio;

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
import com.example.demo.enums.MetodoPago;
import com.example.demo.enums.TipoMovimientoTesoreria;
import com.example.demo.modelo.Caja;
import com.example.demo.modelo.MovimientoTesoreria;
import com.example.demo.modelo.Vendedor;
import com.example.demo.modelo.Venta;
import com.example.demo.repositorio.CajaRepository;
import com.example.demo.repositorio.MovimientoTesoreriaRepository;
import com.example.demo.repositorio.UsuarioRepository;
import com.example.demo.repositorio.VendedorRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TesoreriaService {

	private static final String DEFAULT_VENDEDOR_USERNAME = "vendedor.pos";
	private static final String DEFAULT_VENDEDOR_PASSWORD = "pos";
	private static final String DEFAULT_VENDEDOR_NOMBRE = "Vendedor POS";

	private final MovimientoTesoreriaRepository movimientoTesoreriaRepository;
	private final CajaRepository cajaRepository;
	private final VendedorRepository vendedorRepository;
	private final UsuarioRepository usuarioRepository;

	public TesoreriaService(
		MovimientoTesoreriaRepository movimientoTesoreriaRepository,
		CajaRepository cajaRepository,
		VendedorRepository vendedorRepository,
		UsuarioRepository usuarioRepository
	) {
		this.movimientoTesoreriaRepository = movimientoTesoreriaRepository;
		this.cajaRepository = cajaRepository;
		this.vendedorRepository = vendedorRepository;
		this.usuarioRepository = usuarioRepository;
	}

	@Transactional(readOnly = true)
	public List<MovimientoTesoreriaDto> listarMovimientos() {
		return movimientoTesoreriaRepository.findAllByOrderByFechaHoraDescIdDesc().stream()
			.map(this::toDto)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<MovimientoTesoreriaDto> listarMovimientosPorCaja(Long cajaId, String type, String status, String search) {
		if (cajaId == null || !cajaRepository.existsById(cajaId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Caja no encontrada");
		}

		String searchTerm = normalizeOptional(search).toLowerCase();
		String typeFilter = normalizeOptional(type).toLowerCase();
		String statusFilter = normalizeOptional(status).toLowerCase();

		return movimientoTesoreriaRepository.findAllByCajaIdOrderByFechaHoraDescIdDesc(cajaId).stream()
			.filter(mov -> {
				if (!searchTerm.isEmpty()) {
					String haystack = String.join(" ",
						normalizeOptional(mov.getConcepto()),
						normalizeOptional(mov.getDetalle()),
						normalizeOptional(mov.getUsuario())
					).toLowerCase();
					if (!haystack.contains(searchTerm)) {
						return false;
					}
				}
				if (!typeFilter.isEmpty() && !"all".equals(typeFilter)) {
					if (!mov.getTipo().name().equalsIgnoreCase(typeFilter)) {
						return false;
					}
				}
				if (!statusFilter.isEmpty() && !"all".equals(statusFilter)) {
					boolean isVoided = Boolean.TRUE.equals(mov.getAnulado());
					if ("active".equals(statusFilter) && isVoided) {
						return false;
					}
					if ("voided".equals(statusFilter) && !isVoided) {
						return false;
					}
				}
				return true;
			})
			.map(this::toDto)
			.toList();
	}

	@Transactional(readOnly = true)
	public TesoreriaResumenDto obtenerResumen() {
		TesoreriaTotals totals = calcularTotales();
		return new TesoreriaResumenDto(
			totals.incomes(),
			totals.sales(),
			totals.expenses(),
			totals.cashInDrawer(),
			totals.transfersDebited()
		);
	}

	@Transactional
	public CierreCajaResponse cerrarCaja(CierreCajaRequest request) {
		Caja caja = cajaRepository.findFirstByEstadoOrderByFechaAperturaDesc(EstadoCaja.ABIERTA)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay caja abierta"));

		BigDecimal efectivoContado = calcularConteoBilletes(request.bills());
		TesoreriaTotals totals = calcularTotales();
		BigDecimal efectivoEsperado = money(totals.cashInDrawer());
		BigDecimal diferencia = money(efectivoContado.subtract(efectivoEsperado));

		caja.setFechaCierre(LocalDateTime.now());
		caja.setEstado(EstadoCaja.CERRADA);
		caja.setMontoCierreCalculado(efectivoEsperado);
		caja.setMontoCierreReal(efectivoContado);
		caja.setDiferencia(diferencia);
		caja.setDetalleCierre(buildDetalleCierre(request.bills()));

		String notes = normalizeOptional(request.notes());
		caja.setObservaciones(notes);

		cajaRepository.save(caja);

		return new CierreCajaResponse(efectivoEsperado, efectivoContado, diferencia, caja.getFechaCierre());
	}

	@Transactional(readOnly = true)
	public CajasCerradasResponse listarCajasCerradas(LocalDate from, LocalDate to, int page, int size) {
		int safeSize = clamp(size, 1, 50, 10);
		int safePage = Math.max(page, 0);
		LocalDateTime fromTime = from != null ? from.atStartOfDay() : null;
		LocalDateTime toExclusive = to != null ? to.plusDays(1).atStartOfDay() : null;

		PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "fechaCierre"));
		Page<Caja> pageResult;
		if (fromTime != null && toExclusive != null) {
			pageResult = cajaRepository.findByEstadoAndFechaCierreBetweenOrderByFechaCierreDesc(
				EstadoCaja.CERRADA,
				fromTime,
				toExclusive,
				pageRequest
			);
		} else if (fromTime != null) {
			pageResult = cajaRepository.findByEstadoAndFechaCierreGreaterThanEqualOrderByFechaCierreDesc(
				EstadoCaja.CERRADA,
				fromTime,
				pageRequest
			);
		} else if (toExclusive != null) {
			pageResult = cajaRepository.findByEstadoAndFechaCierreLessThanOrderByFechaCierreDesc(
				EstadoCaja.CERRADA,
				toExclusive,
				pageRequest
			);
		} else {
			pageResult = cajaRepository.findByEstadoOrderByFechaCierreDesc(EstadoCaja.CERRADA, pageRequest);
		}

		List<CajaCerradaDto> items = pageResult.getContent().stream()
			.map(this::toCajaCerradaDto)
			.toList();

		return new CajasCerradasResponse(items, safePage, safeSize, pageResult.hasNext());
	}

	@Transactional(readOnly = true)
	public EstadoCajaDto obtenerEstadoCaja() {
		return cajaRepository.findFirstByEstadoOrderByFechaAperturaDesc(EstadoCaja.ABIERTA)
			.map(caja -> new EstadoCajaDto(
				true,
				caja.getId(),
				caja.getFechaApertura(),
				defaultZero(caja.getMontoApertura())
			))
			.orElseGet(() -> new EstadoCajaDto(false, null, null, BigDecimal.ZERO));
	}

	@Transactional
	public AperturaCajaResponse abrirCaja(AperturaCajaRequest request) {
		if (cajaRepository.findFirstByEstadoOrderByFechaAperturaDesc(EstadoCaja.ABIERTA).isPresent()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ya hay una caja abierta");
		}

		BigDecimal amount = money(defaultZero(request.amount()));
		if (amount.compareTo(BigDecimal.ZERO) < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monto de base invalido");
		}

		Vendedor responsable = resolveOrCreateVendedor();

		Caja caja = new Caja();
		caja.setFechaApertura(LocalDateTime.now());
		caja.setMontoApertura(amount);
		caja.setTotalIngresos(BigDecimal.ZERO);
		caja.setTotalEgresos(BigDecimal.ZERO);
		caja.setTotalVentasEfectivo(BigDecimal.ZERO);
		caja.setTotalVentasTarjeta(BigDecimal.ZERO);
		caja.setTotalVentasTransferencia(BigDecimal.ZERO);
		caja.setMontoCierreCalculado(amount);
		caja.setMontoCierreReal(BigDecimal.ZERO);
		caja.setDiferencia(BigDecimal.ZERO);
		caja.setEstado(EstadoCaja.ABIERTA);
		caja.setResponsable(responsable);
		caja.setObservaciones(normalizeOptional(request.notes()));
		Caja saved = cajaRepository.save(caja);

		movimientoTesoreriaRepository.save(MovimientoTesoreria.builder()
			.fechaHora(LocalDateTime.now())
			.tipo(TipoMovimientoTesoreria.INGRESO)
			.concepto("Cambio Inicial / Base")
			.detalle("Apertura de caja")
			.monto(amount)
			.anulado(Boolean.FALSE)
			.caja(saved)
			.afectaCaja(Boolean.TRUE)
			.usuario(normalizeOptional(responsable.getNombreCompleto()))
			.build());

		return new AperturaCajaResponse(saved.getId(), saved.getFechaApertura(), saved.getMontoApertura());
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

		String concept = normalizeRequired(request.concept(), "Concepto requerido");
		Caja caja = cajaRepository.findFirstByEstadoOrderByFechaAperturaDesc(EstadoCaja.ABIERTA).orElse(null);
		if (caja == null) {
			if (request.type() == TipoMovimientoTesoreria.INGRESO && isCambioInicial(concept)) {
				caja = abrirCajaDesdeTesoreria(money(amount));
			} else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay caja abierta");
			}
		}

		BigDecimal signedAmount = request.type() == TipoMovimientoTesoreria.EGRESO
			? amount.negate()
			: amount;

		MovimientoTesoreria saved = movimientoTesoreriaRepository.save(MovimientoTesoreria.builder()
			.fechaHora(LocalDateTime.now())
			.tipo(request.type())
			.concepto(concept)
			.detalle(normalizeOptional(request.detail()))
			.monto(signedAmount)
			.anulado(Boolean.FALSE)
			.caja(caja)
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
			.anulado(Boolean.FALSE)
			.caja(venta.getCaja())
			.afectaCaja(afectaCaja)
			.metodoPago(metodoPago)
			.usuario(normalizeOptional(usuario))
			.venta(venta)
			.build());
	}

	@Transactional
	public void anularMovimientoManual(Long id) {
		MovimientoTesoreria movimiento = movimientoTesoreriaRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));

		if (movimiento.getTipo() == TipoMovimientoTesoreria.VENTA) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las ventas se anulan desde su modulo");
		}
		if (Boolean.TRUE.equals(movimiento.getAnulado())) {
			return;
		}
		movimiento.setAnulado(Boolean.TRUE);
		movimientoTesoreriaRepository.save(movimiento);
	}

	@Transactional
	public void anularMovimientosVenta(Long ventaId) {
		movimientoTesoreriaRepository.findAllByVentaId(ventaId).forEach(movimiento -> {
			if (!Boolean.TRUE.equals(movimiento.getAnulado())) {
				movimiento.setAnulado(Boolean.TRUE);
				movimientoTesoreriaRepository.save(movimiento);
			}
		});
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
			movimiento.getTipo() != TipoMovimientoTesoreria.VENTA,
			Boolean.TRUE.equals(movimiento.getAnulado())
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

	private CajaCerradaDto toCajaCerradaDto(Caja caja) {
		return new CajaCerradaDto(
			caja.getId(),
			caja.getFechaCierre(),
			defaultZero(caja.getMontoCierreCalculado()),
			defaultZero(caja.getMontoCierreReal()),
			defaultZero(caja.getDiferencia()),
			caja.getResponsable() != null ? normalizeOptional(caja.getResponsable().getNombreCompleto()) : "",
			normalizeOptional(caja.getObservaciones())
		);
	}

	private BigDecimal money(BigDecimal value) {
		return defaultZero(value).setScale(2, RoundingMode.HALF_UP);
	}

	private Vendedor resolveOrCreateVendedor() {
		return vendedorRepository.findFirstByActivoTrueOrderByIdAsc()
			.orElseGet(() -> {
				String username = DEFAULT_VENDEDOR_USERNAME;
				if (usuarioRepository.existsByUsername(username)) {
					username = DEFAULT_VENDEDOR_USERNAME + System.currentTimeMillis();
				}

				Vendedor vendedor = new Vendedor();
				vendedor.setNombreCompleto(DEFAULT_VENDEDOR_NOMBRE);
				vendedor.setUsername(username);
				vendedor.setPassword(DEFAULT_VENDEDOR_PASSWORD);
				vendedor.setRol(com.example.demo.enums.RolUsuario.VENDEDOR);
				vendedor.setActivo(true);
				return vendedorRepository.save(vendedor);
			});
	}

	private boolean isCambioInicial(String concept) {
		String normalized = normalizeOptional(concept).toLowerCase();
		return normalized.equals("cambio inicial / base");
	}

	private Caja abrirCajaDesdeTesoreria(BigDecimal amount) {
		Vendedor responsable = resolveOrCreateVendedor();
		Caja caja = new Caja();
		caja.setFechaApertura(LocalDateTime.now());
		caja.setMontoApertura(amount);
		caja.setTotalIngresos(BigDecimal.ZERO);
		caja.setTotalEgresos(BigDecimal.ZERO);
		caja.setTotalVentasEfectivo(BigDecimal.ZERO);
		caja.setTotalVentasTarjeta(BigDecimal.ZERO);
		caja.setTotalVentasTransferencia(BigDecimal.ZERO);
		caja.setMontoCierreCalculado(amount);
		caja.setMontoCierreReal(BigDecimal.ZERO);
		caja.setDiferencia(BigDecimal.ZERO);
		caja.setEstado(EstadoCaja.ABIERTA);
		caja.setResponsable(responsable);
		caja.setObservaciones("Caja abierta desde tesoreria");
		Caja saved = cajaRepository.save(caja);
		return saved;
	}

	private BigDecimal calcularConteoBilletes(List<ConteoBilleteRequest> bills) {
		if (bills == null || bills.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe ingresar el recuento de billetes");
		}

		BigDecimal total = BigDecimal.ZERO;
		for (ConteoBilleteRequest bill : bills) {
			if (bill == null) {
				continue;
			}
			BigDecimal denom = defaultZero(bill.denomination());
			Integer qty = bill.quantity();
			if (qty == null || qty < 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad de billetes invalida");
			}
			total = total.add(denom.multiply(BigDecimal.valueOf(qty)));
		}
		return money(total);
	}

	private String buildDetalleCierre(List<ConteoBilleteRequest> bills) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"billetes\":{");
		boolean first = true;
		for (ConteoBilleteRequest bill : bills) {
			if (bill == null) {
				continue;
			}
			if (!first) {
				sb.append(',');
			}
			first = false;
			String denom = defaultZero(bill.denomination()).stripTrailingZeros().toPlainString();
			sb.append('"').append(denom).append('"').append(':').append(bill.quantity() == null ? 0 : bill.quantity());
		}
		sb.append("}}");
		return sb.toString();
	}

	private TesoreriaTotals calcularTotales() {
		BigDecimal totalIngresos = BigDecimal.ZERO;
		BigDecimal totalVentas = BigDecimal.ZERO;
		BigDecimal totalEgresos = BigDecimal.ZERO;
		BigDecimal efectivoCaja = BigDecimal.ZERO;
		BigDecimal transferencias = BigDecimal.ZERO;

		for (MovimientoTesoreria movimiento : movimientoTesoreriaRepository.findAll()) {
			if (Boolean.TRUE.equals(movimiento.getAnulado())) {
				continue;
			}

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

			if (movimiento.getMetodoPago() == MetodoPago.TRANSFERENCIA) {
				transferencias = transferencias.add(absOrZero(movimiento.getMonto()));
			}
		}

		return new TesoreriaTotals(totalIngresos, totalVentas, totalEgresos, efectivoCaja, transferencias);
	}

	private record TesoreriaTotals(
		BigDecimal incomes,
		BigDecimal sales,
		BigDecimal expenses,
		BigDecimal cashInDrawer,
		BigDecimal transfersDebited
	) {}

	private int clamp(int value, int min, int max, int defaultValue) {
		if (value <= 0) {
			return defaultValue;
		}
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}
}
