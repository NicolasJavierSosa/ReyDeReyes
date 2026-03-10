package com.example.demo.servicio;

import com.example.demo.dto.VentaCheckoutItemRequest;
import com.example.demo.dto.VentaCheckoutPagoRequest;
import com.example.demo.dto.VentaCheckoutRequest;
import com.example.demo.dto.VentaCheckoutResponse;
import com.example.demo.dto.VentaDetalleItemDto;
import com.example.demo.dto.VentaDetalleResponse;
import com.example.demo.enums.EstadoCaja;
import com.example.demo.enums.EstadoVenta;
import com.example.demo.enums.MetodoPago;
import com.example.demo.enums.RolUsuario;
import com.example.demo.modelo.Caja;
import com.example.demo.modelo.DetalleVenta;
import com.example.demo.modelo.Pago;
import com.example.demo.modelo.Producto;
import com.example.demo.modelo.Stock;
import com.example.demo.modelo.Vendedor;
import com.example.demo.modelo.Venta;
import com.example.demo.repositorio.CajaRepository;
import com.example.demo.repositorio.ProductoRepository;
import com.example.demo.repositorio.StockRepository;
import com.example.demo.repositorio.UsuarioRepository;
import com.example.demo.repositorio.VendedorRepository;
import com.example.demo.repositorio.VentaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VentaService {

	private static final String DEFAULT_VENDEDOR_USERNAME = "vendedor.pos";
	private static final String DEFAULT_VENDEDOR_PASSWORD = "pos";
	private static final String DEFAULT_VENDEDOR_NOMBRE = "Vendedor POS";

	private final ProductoRepository productoRepository;
	private final StockRepository stockRepository;
	private final VentaRepository ventaRepository;
	private final CajaRepository cajaRepository;
	private final VendedorRepository vendedorRepository;
	private final UsuarioRepository usuarioRepository;
	private final TesoreriaService tesoreriaService;

	public VentaService(
		ProductoRepository productoRepository,
		StockRepository stockRepository,
		VentaRepository ventaRepository,
		CajaRepository cajaRepository,
		VendedorRepository vendedorRepository,
		UsuarioRepository usuarioRepository,
		TesoreriaService tesoreriaService
	) {
		this.productoRepository = productoRepository;
		this.stockRepository = stockRepository;
		this.ventaRepository = ventaRepository;
		this.cajaRepository = cajaRepository;
		this.vendedorRepository = vendedorRepository;
		this.usuarioRepository = usuarioRepository;
		this.tesoreriaService = tesoreriaService;
	}

	@Transactional
	public VentaCheckoutResponse checkout(VentaCheckoutRequest request) {
		if (request.items() == null || request.items().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta no tiene productos");
		}

		Vendedor vendedor = resolveOrCreateVendedor();
		Caja caja = resolveOrCreateCajaAbierta(vendedor);

		Venta venta = new Venta();
		LocalDateTime now = LocalDateTime.now();
		venta.setFechaHora(now);
		venta.setEstado(EstadoVenta.COMPLETADA);
		venta.setVendedor(vendedor);
		venta.setCaja(caja);
		venta.setObservaciones("Venta POS");

		List<DetalleVenta> detalles = new ArrayList<>();
		BigDecimal subtotal = BigDecimal.ZERO;

		for (VentaCheckoutItemRequest item : request.items()) {
			String barcode = normalizeRequired(item.barcode(), "Codigo de barra requerido");
			BigDecimal quantity = scaleQty(defaultZero(item.quantity()));
			if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad invalida para " + barcode);
			}

			Producto producto = productoRepository.findByCodigoBarraAndActivoTrue(barcode)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado: " + barcode));
			validarComboDisponible(producto);

			BigDecimal precioUnitario = money(defaultZero(producto.getPrecioVenta()));
			BigDecimal subtotalLinea = money(precioUnitario.multiply(quantity));

			DetalleVenta detalle = new DetalleVenta();
			detalle.setVenta(venta);
			detalle.setProducto(producto);
			detalle.setCantidad(quantity);
			detalle.setPrecioUnitario(precioUnitario);
			detalle.setDescuentoLinea(BigDecimal.ZERO);
			detalle.setSubtotal(subtotalLinea);
			detalle.setTotalLinea(subtotalLinea);
			detalles.add(detalle);

			subtotal = subtotal.add(subtotalLinea);
			descontarStock(producto, quantity);
		}

		BigDecimal descuento = money(defaultZero(request.discount()));
		if (descuento.compareTo(subtotal) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento no puede superar el subtotal");
		}

		BigDecimal total = money(subtotal.subtract(descuento));
		VentaCheckoutPagoRequest pagoRequest = request.payment();
		MetodoPago metodoPago = pagoRequest.method();
		if (metodoPago != MetodoPago.EFECTIVO && metodoPago != MetodoPago.TRANSFERENCIA) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metodo de pago invalido para el POS");
		}

		BigDecimal entregado = null;
		BigDecimal cambio = BigDecimal.ZERO;
		boolean afectaCaja = metodoPago == MetodoPago.EFECTIVO;
		if (metodoPago == MetodoPago.EFECTIVO) {
			entregado = money(defaultZero(pagoRequest.amountReceived()));
			if (entregado.compareTo(total) < 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto entregado es menor al total");
			}
			cambio = money(entregado.subtract(total));
		}

		venta.setSubtotal(subtotal);
		venta.setDescuentoTotal(descuento);
		venta.setTotal(total);
		venta.getDetalles().clear();
		venta.getDetalles().addAll(detalles);

		Pago pago = new Pago();
		pago.setVenta(venta);
		pago.setMetodo(metodoPago);
		pago.setMonto(total);
		pago.setEntregado(entregado);
		pago.setCambio(cambio);
		pago.setFechaHora(now);
		venta.getPagos().clear();
		venta.getPagos().add(pago);

		Venta saved = ventaRepository.save(venta);
		actualizarTotalesCaja(caja, metodoPago, total);
		tesoreriaService.registrarVenta(saved, metodoPago, total, afectaCaja, vendedor.getNombreCompleto());

		return new VentaCheckoutResponse(
			saved.getId(),
			String.format("%06d", saved.getId()),
			subtotal,
			descuento,
			total,
			metodoPago,
			entregado,
			cambio,
			now
		);
	}

	@Transactional(readOnly = true)
	public VentaDetalleResponse obtenerDetalle(Long ventaId) {
		Venta venta = ventaRepository.findById(ventaId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada"));

		MetodoPago metodoPago = null;
		try {
			metodoPago = resolveMetodoPago(venta);
		} catch (ResponseStatusException ignored) {
			// Venta sin pago
		}

		List<VentaDetalleItemDto> items = venta.getDetalles().stream()
			.map(detalle -> new VentaDetalleItemDto(
				detalle.getProducto() != null ? detalle.getProducto().getCodigoBarra() : "",
				detalle.getProducto() != null ? detalle.getProducto().getNombre() : "",
				defaultZero(detalle.getCantidad()),
				defaultZero(detalle.getPrecioUnitario()),
				defaultZero(detalle.getSubtotal()),
				defaultZero(detalle.getTotalLinea())
			))
			.toList();

		return new VentaDetalleResponse(
			venta.getId(),
			String.format("%06d", venta.getId()),
			venta.getFechaHora(),
			defaultZero(venta.getSubtotal()),
			defaultZero(venta.getDescuentoTotal()),
			defaultZero(venta.getTotal()),
			venta.getEstado(),
			metodoPago,
			items
		);
	}

	@Transactional
	public void anularVenta(Long ventaId) {
		Venta venta = ventaRepository.findById(ventaId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada"));

		if (venta.getEstado() == EstadoVenta.ANULADA) {
			return;
		}

		if (venta.getEstado() != EstadoVenta.COMPLETADA) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta no esta completada");
		}

		for (DetalleVenta detalle : venta.getDetalles()) {
			reponerStock(detalle.getProducto(), defaultZero(detalle.getCantidad()));
		}

		MetodoPago metodoPago = resolveMetodoPago(venta);
		ajustarCajaPorAnulacion(venta.getCaja(), metodoPago, defaultZero(venta.getTotal()));

		venta.setEstado(EstadoVenta.ANULADA);
		venta.setObservaciones(appendObservacion(venta.getObservaciones(), "Venta dada de baja"));
		ventaRepository.save(venta);

		tesoreriaService.anularMovimientosVenta(ventaId);
	}

	private void descontarStock(Producto producto, BigDecimal quantity) {
		Stock stock = stockRepository.findByProductoId(producto.getId()).orElse(null);
		if (stock == null) {
			return;
		}

		BigDecimal actual = defaultZero(stock.getCantidadActual());
		BigDecimal nuevo = actual.subtract(quantity);
		if (nuevo.compareTo(BigDecimal.ZERO) < 0) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Stock insuficiente para " + producto.getNombre() + ". Stock actual: " + actual
			);
		}

		stock.setCantidadActual(scaleQty(nuevo));
		stock.setUltimoMovimiento(LocalDateTime.now());
		stockRepository.save(stock);
	}

	private void reponerStock(Producto producto, BigDecimal quantity) {
		if (producto == null) {
			return;
		}
		Stock stock = stockRepository.findByProductoId(producto.getId()).orElse(null);
		if (stock == null) {
			return;
		}

		BigDecimal actual = defaultZero(stock.getCantidadActual());
		BigDecimal nuevo = actual.add(quantity);
		stock.setCantidadActual(scaleQty(nuevo));
		stock.setUltimoMovimiento(LocalDateTime.now());
		stockRepository.save(stock);
	}

	private void validarComboDisponible(Producto producto) {
		if (producto == null || !producto.isCombo()) {
			return;
		}
		if (isGroupCombo(producto)) {
			Long categoryId = producto.getComboGrupoCategoria().getId();
			int required = producto.getComboGrupoCantidad() != null ? producto.getComboGrupoCantidad() : 0;
			if (required <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Combo sin cantidad definida");
			}
			BigDecimal totalStock = sumStockByCategoryId(categoryId);
			if (totalStock.compareTo(BigDecimal.valueOf(required)) < 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Combo sin stock disponible");
			}
			return;
		}

		List<Producto> items = producto.getComboProductos();
		if (items == null || items.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Combo sin productos disponibles");
		}

		for (Producto item : items) {
			if (item == null || !Boolean.TRUE.equals(item.getActivo())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Combo no disponible: producto inactivo");
			}
			Stock stock = stockRepository.findByProductoId(item.getId()).orElse(null);
			BigDecimal actual = stock != null && stock.getCantidadActual() != null
				? stock.getCantidadActual()
				: BigDecimal.ZERO;
			if (actual.compareTo(BigDecimal.ZERO) <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Combo sin stock disponible");
			}
		}
	}

	private boolean isGroupCombo(Producto combo) {
		return combo.getComboGrupoCategoria() != null
			&& combo.getComboGrupoCantidad() != null
			&& combo.getComboGrupoCantidad() > 0;
	}

	private BigDecimal sumStockByCategoryId(Long categoryId) {
		if (categoryId == null) {
			return BigDecimal.ZERO;
		}
		List<Producto> productos = productoRepository.findByCategoriaIdAndActivoTrue(categoryId);
		BigDecimal total = BigDecimal.ZERO;
		for (Producto producto : productos) {
			if (producto == null || producto.isCombo()) {
				continue;
			}
			Stock stock = stockRepository.findByProductoId(producto.getId()).orElse(null);
			BigDecimal actual = stock != null && stock.getCantidadActual() != null
				? stock.getCantidadActual()
				: BigDecimal.ZERO;
			total = total.add(actual);
		}
		return total;
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
				vendedor.setRol(RolUsuario.VENDEDOR);
				vendedor.setActivo(true);
				return vendedorRepository.save(vendedor);
			});
	}

	private Caja resolveOrCreateCajaAbierta(Vendedor vendedor) {
		return cajaRepository.findFirstByEstadoOrderByFechaAperturaDesc(EstadoCaja.ABIERTA)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay caja abierta"));
	}

	private void actualizarTotalesCaja(Caja caja, MetodoPago metodoPago, BigDecimal total) {
		BigDecimal totalEfectivo = defaultZero(caja.getTotalVentasEfectivo());
		BigDecimal totalTarjeta = defaultZero(caja.getTotalVentasTarjeta());
		BigDecimal totalTransferencia = defaultZero(caja.getTotalVentasTransferencia());
		BigDecimal totalIngresos = defaultZero(caja.getTotalIngresos());
		BigDecimal totalEgresos = defaultZero(caja.getTotalEgresos());
		BigDecimal apertura = defaultZero(caja.getMontoApertura());

		if (metodoPago == MetodoPago.EFECTIVO) {
			totalEfectivo = money(totalEfectivo.add(total));
		} else if (metodoPago == MetodoPago.TRANSFERENCIA) {
			totalTransferencia = money(totalTransferencia.add(total));
		} else {
			totalTarjeta = money(totalTarjeta.add(total));
		}

		caja.setTotalVentasEfectivo(totalEfectivo);
		caja.setTotalVentasTarjeta(totalTarjeta);
		caja.setTotalVentasTransferencia(totalTransferencia);
		caja.setMontoCierreCalculado(money(apertura.add(totalIngresos).add(totalEfectivo).subtract(totalEgresos)));
		cajaRepository.save(caja);
	}

	private void ajustarCajaPorAnulacion(Caja caja, MetodoPago metodoPago, BigDecimal total) {
		BigDecimal totalEfectivo = defaultZero(caja.getTotalVentasEfectivo());
		BigDecimal totalTarjeta = defaultZero(caja.getTotalVentasTarjeta());
		BigDecimal totalTransferencia = defaultZero(caja.getTotalVentasTransferencia());
		BigDecimal totalIngresos = defaultZero(caja.getTotalIngresos());
		BigDecimal totalEgresos = defaultZero(caja.getTotalEgresos());
		BigDecimal apertura = defaultZero(caja.getMontoApertura());

		if (metodoPago == MetodoPago.EFECTIVO) {
			totalEfectivo = maxZero(totalEfectivo.subtract(total));
		} else if (metodoPago == MetodoPago.TRANSFERENCIA) {
			totalTransferencia = maxZero(totalTransferencia.subtract(total));
		} else {
			totalTarjeta = maxZero(totalTarjeta.subtract(total));
		}

		caja.setTotalVentasEfectivo(totalEfectivo);
		caja.setTotalVentasTarjeta(totalTarjeta);
		caja.setTotalVentasTransferencia(totalTransferencia);
		caja.setMontoCierreCalculado(money(apertura.add(totalIngresos).add(totalEfectivo).subtract(totalEgresos)));
		cajaRepository.save(caja);
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

	private BigDecimal money(BigDecimal value) {
		return defaultZero(value).setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal scaleQty(BigDecimal value) {
		return defaultZero(value).setScale(3, RoundingMode.HALF_UP);
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private MetodoPago resolveMetodoPago(Venta venta) {
		if (venta == null || venta.getPagos() == null || venta.getPagos().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta no tiene pagos");
		}
		return Optional.ofNullable(venta.getPagos().get(0).getMetodo())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metodo de pago no definido"));
	}

	private BigDecimal maxZero(BigDecimal value) {
		return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
	}

	private String appendObservacion(String current, String addition) {
		String base = normalizeOptional(current);
		if (base.isEmpty()) {
			return addition;
		}
		return base + " | " + addition;
	}
}
