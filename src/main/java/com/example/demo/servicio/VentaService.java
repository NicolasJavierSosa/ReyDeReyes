package com.example.demo.servicio;

import static com.example.demo.util.Valores.cantidad;
import static com.example.demo.util.Valores.cero;
import static com.example.demo.util.Valores.dinero;
import static com.example.demo.util.Valores.error;
import static com.example.demo.util.Valores.noEncontrado;
import static com.example.demo.util.Valores.requerido;
import static com.example.demo.util.Valores.texto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.VentaCheckoutItemRequest;
import com.example.demo.dto.VentaCheckoutPagoRequest;
import com.example.demo.dto.VentaCheckoutRequest;
import com.example.demo.dto.VentaCheckoutResponse;
import com.example.demo.dto.VentaDetalleItemDto;
import com.example.demo.dto.VentaDetalleResponse;
import com.example.demo.dto.VentaResumenDto;
import com.example.demo.enums.EstadoCaja;
import com.example.demo.enums.EstadoVenta;
import com.example.demo.enums.TipoMovimientoStock;
import com.example.demo.modelo.Caja;
import com.example.demo.modelo.ComboItem;
import com.example.demo.modelo.DetalleVenta;
import com.example.demo.modelo.MetodoPago;
import com.example.demo.modelo.MovimientoStock;
import com.example.demo.modelo.Pago;
import com.example.demo.modelo.Producto;
import com.example.demo.modelo.Venta;
import com.example.demo.repositorio.MetodoPagoRepository;
import com.example.demo.repositorio.MovimientoStockRepository;
import com.example.demo.repositorio.ProductoRepository;
import com.example.demo.repositorio.VentaRepository;
import com.example.demo.util.Valores;

@Service
public class VentaService {

	private final ProductoRepository productoRepository;
	private final VentaRepository ventaRepository;
	private final MetodoPagoRepository metodoPagoRepository;
	private final MovimientoStockRepository movimientoStockRepository;
	private final StockService stockService;
	private final TesoreriaService tesoreriaService;

	public VentaService(
		ProductoRepository productoRepository,
		VentaRepository ventaRepository,
		MetodoPagoRepository metodoPagoRepository,
		MovimientoStockRepository movimientoStockRepository,
		StockService stockService,
		TesoreriaService tesoreriaService
	) {
		this.productoRepository = productoRepository;
		this.ventaRepository = ventaRepository;
		this.metodoPagoRepository = metodoPagoRepository;
		this.movimientoStockRepository = movimientoStockRepository;
		this.stockService = stockService;
		this.tesoreriaService = tesoreriaService;
	}

	@Transactional
	public VentaCheckoutResponse checkout(VentaCheckoutRequest request) {
		if (request.items() == null || request.items().isEmpty()) {
			throw error("La venta no tiene productos");
		}
		Caja caja = tesoreriaService.cajaAbierta();
		MetodoPago metodo = resolverMetodo(request.payment());

		// 1. Productos de cada linea
		List<Linea> lineas = new ArrayList<>();
		for (VentaCheckoutItemRequest item : request.items()) {
			String barcode = requerido(item.barcode(), "Codigo de barra requerido");
			BigDecimal quantity = cantidad(item.quantity());
			if (quantity.signum() <= 0) {
				throw error("Cantidad invalida para " + barcode);
			}
			Producto producto = productoRepository.findByCodigoBarraAndActivoTrue(barcode)
				.orElseThrow(() -> noEncontrado("Producto no encontrado: " + barcode));
			if (producto.isCombo() && quantity.stripTrailingZeros().scale() > 0) {
				throw error("Los combos se venden por unidad entera: " + producto.getNombre());
			}
			lineas.add(new Linea(producto, quantity, item.components()));
		}

		// 2. Bloquear todos los productos cuyo stock puede tocarse y leer su stock
		Set<UUID> involucrados = new LinkedHashSet<>();
		for (Linea linea : lineas) {
			Producto p = linea.producto;
			if (!p.isCombo()) {
				involucrados.add(p.getId());
			} else if (p.isComboDeGrupo()) {
				productoRepository.findByCategoriaIdAndActivoTrueAndComboFalse(p.getComboGrupoCategoria().getId())
					.forEach(c -> involucrados.add(c.getId()));
			} else {
				p.getComboItems().forEach(c -> involucrados.add(c.productoId()));
			}
		}
		stockService.bloquear(involucrados);
		Map<UUID, BigDecimal> stockInicial = stockService.stockDe(involucrados);
		Map<UUID, BigDecimal> disponible = new HashMap<>(stockInicial);

		// 3. Resolver que se descuenta de cada linea (los combos descuentan sus componentes)
		Map<UUID, Producto> productos = productoRepository.findAllById(involucrados).stream()
			.collect(Collectors.toMap(Producto::getId, p -> p));
		Map<UUID, BigDecimal> consumo = new LinkedHashMap<>();
		for (Linea linea : lineas) {
			linea.consumo = resolverConsumo(linea, productos, disponible);
			linea.consumo.forEach((id, qty) -> {
				consumo.merge(id, qty, BigDecimal::add);
				disponible.merge(id, qty.negate(), BigDecimal::add);
			});
		}
		for (Map.Entry<UUID, BigDecimal> entry : consumo.entrySet()) {
			BigDecimal stock = cero(stockInicial.get(entry.getKey()));
			if (stock.compareTo(entry.getValue()) < 0) {
				Producto p = productos.get(entry.getKey());
				throw error("Stock insuficiente para " + p.getNombre() + ". Stock actual: " + stock.stripTrailingZeros().toPlainString());
			}
		}

		// 4. Importes, con el descuento general repartido entre las lineas
		LocalDateTime now = LocalDateTime.now();
		Venta venta = new Venta();
		venta.setNumero(ventaRepository.siguienteNumero());
		venta.setFechaHora(now);
		venta.setEstado(EstadoVenta.COMPLETADA);
		venta.setCaja(caja);
		venta.setObservaciones("Venta POS");

		BigDecimal subtotal = BigDecimal.ZERO;
		for (Linea linea : lineas) {
			linea.subtotal = dinero(cero(linea.producto.getPrecioVenta()).multiply(linea.cantidad));
			subtotal = subtotal.add(linea.subtotal);
		}
		BigDecimal descuento = dinero(request.discount());
		if (descuento.signum() < 0) {
			throw error("Descuento invalido");
		}
		if (descuento.compareTo(subtotal) > 0) {
			throw error("El descuento no puede superar el subtotal");
		}
		repartirDescuento(lineas, descuento, subtotal);
		BigDecimal total = dinero(subtotal.subtract(descuento));

		for (Linea linea : lineas) {
			DetalleVenta detalle = new DetalleVenta();
			detalle.setVenta(venta);
			detalle.setProducto(linea.producto);
			detalle.setCantidad(linea.cantidad);
			detalle.setPrecioUnitario(dinero(linea.producto.getPrecioVenta()));
			detalle.setCostoUnitario(costoUnitario(linea, productos));
			detalle.setSubtotal(linea.subtotal);
			detalle.setDescuentoLinea(linea.descuento);
			detalle.setTotalLinea(dinero(linea.subtotal.subtract(linea.descuento)));
			if (linea.producto.isCombo()) {
				detalle.setComponentes(linea.consumo.entrySet().stream()
					.map(e -> new ComboItem(e.getKey(), e.getValue()))
					.collect(Collectors.toCollection(ArrayList::new)));
			}
			venta.getDetalles().add(detalle);
		}

		// 5. Pago
		BigDecimal entregado = null;
		BigDecimal cambio = BigDecimal.ZERO;
		if (Boolean.TRUE.equals(metodo.getAfectaCaja())) {
			entregado = request.payment().amountReceived() == null ? total : dinero(request.payment().amountReceived());
			if (entregado.compareTo(total) < 0) {
				throw error("El monto entregado es menor al total");
			}
			cambio = dinero(entregado.subtract(total));
		}
		Pago pago = new Pago();
		pago.setVenta(venta);
		pago.setMetodoPago(metodo);
		pago.setMetodoNombre(metodo.getNombre());
		pago.setMonto(total);
		pago.setEntregado(entregado);
		pago.setCambio(cambio);
		pago.setFechaHora(now);
		venta.getPagos().add(pago);

		venta.setSubtotal(dinero(subtotal));
		venta.setDescuentoTotal(descuento);
		venta.setTotal(total);
		Venta saved = ventaRepository.save(venta);

		// 6. Movimientos de stock y de tesoreria
		String motivo = "Venta #" + Valores.ticket(saved.getNumero());
		consumo.forEach((id, qty) -> stockService.registrar(productos.get(id), TipoMovimientoStock.VENTA, qty.negate(), motivo, saved, null));
		tesoreriaService.registrarVenta(saved, metodo, total);

		return new VentaCheckoutResponse(
			saved.getId(),
			Valores.ticket(saved.getNumero()),
			saved.getSubtotal(),
			descuento,
			total,
			metodo.getCodigo(),
			entregado,
			cambio,
			now
		);
	}

	@Transactional(readOnly = true)
	public VentaDetalleResponse obtenerDetalle(UUID ventaId) {
		Venta venta = ventaRepository.findById(ventaId)
			.orElseThrow(() -> noEncontrado("Venta no encontrada"));
		String metodo = venta.getPagos().isEmpty() ? null : venta.getPagos().get(0).getMetodoNombre();
		List<VentaDetalleItemDto> items = venta.getDetalles().stream()
			.map(detalle -> new VentaDetalleItemDto(
				detalle.getProducto().getCodigoBarra(),
				detalle.getProducto().getNombre(),
				cero(detalle.getCantidad()),
				cero(detalle.getPrecioUnitario()),
				cero(detalle.getSubtotal()),
				cero(detalle.getTotalLinea())
			))
			.toList();
		return new VentaDetalleResponse(
			venta.getId(),
			Valores.ticket(venta.getNumero()),
			venta.getFechaHora(),
			cero(venta.getSubtotal()),
			cero(venta.getDescuentoTotal()),
			cero(venta.getTotal()),
			venta.getEstado(),
			metodo,
			items
		);
	}

	@Transactional(readOnly = true)
	public List<VentaResumenDto> listar(java.time.LocalDate desde, java.time.LocalDate hasta) {
		if (desde == null || hasta == null || hasta.isBefore(desde)) {
			throw error("Rango de fechas invalido");
		}
		return ventaRepository.findByFechaHoraEntre(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay()).stream()
			.map(v -> new VentaResumenDto(
				v.getId(),
				Valores.ticket(v.getNumero()),
				v.getFechaHora(),
				cero(v.getTotal()),
				v.getEstado(),
				v.getPagos().isEmpty() ? null : v.getPagos().get(0).getMetodoNombre(),
				v.getDetalles().size()
			))
			.toList();
	}

	/** Anula una venta de la caja abierta: devuelve el stock y anula su movimiento de tesoreria. */
	@Transactional
	public void anularVenta(UUID ventaId) {
		Venta venta = ventaRepository.findById(ventaId)
			.orElseThrow(() -> noEncontrado("Venta no encontrada"));
		if (venta.getEstado() == EstadoVenta.ANULADA) {
			return;
		}
		if (!"LOCAL".equals(venta.getCaja().getOrigen())) {
			throw error("Esta venta se hizo desde la web y no se puede anular desde el local");
		}
		if (venta.getCaja().getEstado() != EstadoCaja.ABIERTA) {
			throw error("La venta pertenece a una caja cerrada y ya no se puede anular");
		}
		String motivo = "Anulacion venta #" + Valores.ticket(venta.getNumero());
		for (MovimientoStock movimiento : movimientoStockRepository.findAllByVentaId(ventaId)) {
			if (movimiento.getTipo() == TipoMovimientoStock.VENTA) {
				stockService.registrar(movimiento.getProducto(), TipoMovimientoStock.ANULACION,
					movimiento.getCantidad().negate(), motivo, venta, null);
			}
		}
		venta.setEstado(EstadoVenta.ANULADA);
		venta.setFechaAnulacion(LocalDateTime.now());
		String obs = texto(venta.getObservaciones());
		venta.setObservaciones(obs.isEmpty() ? "Venta dada de baja" : obs + " | Venta dada de baja");
		ventaRepository.save(venta);
		tesoreriaService.anularMovimientosVenta(ventaId);
	}

	// ------------------------------------------------------------------ auxiliares

	private MetodoPago resolverMetodo(VentaCheckoutPagoRequest pago) {
		if (pago == null) {
			throw error("Falta el metodo de pago");
		}
		String codigo = requerido(pago.method(), "Metodo de pago requerido");
		MetodoPago metodo = metodoPagoRepository.findByCodigoIgnoreCase(codigo)
			.orElseThrow(() -> error("Metodo de pago invalido: " + codigo));
		if (!Boolean.TRUE.equals(metodo.getActivo())) {
			throw error("Metodo de pago inactivo: " + metodo.getNombre());
		}
		return metodo;
	}

	private Map<UUID, BigDecimal> resolverConsumo(Linea linea, Map<UUID, Producto> productos, Map<UUID, BigDecimal> disponible) {
		Producto producto = linea.producto;
		Map<UUID, BigDecimal> consumo = new LinkedHashMap<>();
		if (!producto.isCombo()) {
			consumo.put(producto.getId(), linea.cantidad);
			return consumo;
		}
		if (!producto.isComboDeGrupo()) {
			if (producto.getComboItems() == null || producto.getComboItems().isEmpty()) {
				throw error("Combo sin productos: " + producto.getNombre());
			}
			for (ComboItem item : producto.getComboItems()) {
				Producto componente = productos.get(item.productoId());
				if (componente == null || !Boolean.TRUE.equals(componente.getActivo())) {
					throw error("Combo no disponible: producto inactivo en " + producto.getNombre());
				}
				BigDecimal porUnidad = cero(item.cantidad()).signum() > 0 ? item.cantidad() : BigDecimal.ONE;
				consumo.merge(item.productoId(), cantidad(porUnidad.multiply(linea.cantidad)), BigDecimal::add);
			}
			return consumo;
		}

		// Combo de grupo: N unidades a elegir de una categoria
		UUID categoriaId = producto.getComboGrupoCategoria().getId();
		BigDecimal requerido = BigDecimal.valueOf(producto.getComboGrupoCantidad()).multiply(linea.cantidad);
		if (linea.componentes != null && !linea.componentes.isEmpty()) {
			BigDecimal elegido = BigDecimal.ZERO;
			for (VentaCheckoutItemRequest comp : linea.componentes) {
				String barcode = requerido(comp.barcode(), "Codigo de barra requerido en el combo");
				Producto componente = productoRepository.findByCodigoBarraAndActivoTrue(barcode)
					.orElseThrow(() -> noEncontrado("Producto no encontrado: " + barcode));
				if (componente.isCombo() || componente.getCategoria() == null
					|| !categoriaId.equals(componente.getCategoria().getId())) {
					throw error(componente.getNombre() + " no pertenece a la categoria del combo");
				}
				BigDecimal qty = cantidad(comp.quantity());
				consumo.merge(componente.getId(), qty, BigDecimal::add);
				productos.putIfAbsent(componente.getId(), componente);
				elegido = elegido.add(qty);
			}
			if (elegido.compareTo(requerido) != 0) {
				throw error("El combo " + producto.getNombre() + " lleva " + requerido.stripTrailingZeros().toPlainString()
					+ " unidades y se eligieron " + elegido.stripTrailingZeros().toPlainString());
			}
			return consumo;
		}
		// Sin eleccion: se toman los productos de la categoria con mas stock
		List<Producto> candidatos = productos.values().stream()
			.filter(p -> !p.isCombo() && Boolean.TRUE.equals(p.getActivo()) && p.getCategoria() != null
				&& categoriaId.equals(p.getCategoria().getId()))
			.sorted(Comparator.comparing((Producto p) -> cero(disponible.get(p.getId()))).reversed()
				.thenComparing(Producto::getNombre))
			.toList();
		BigDecimal faltante = requerido;
		for (Producto candidato : candidatos) {
			if (faltante.signum() <= 0) {
				break;
			}
			BigDecimal stock = cero(disponible.get(candidato.getId())).setScale(0, RoundingMode.DOWN);
			BigDecimal tomar = stock.min(faltante);
			if (tomar.signum() > 0) {
				consumo.put(candidato.getId(), cantidad(tomar));
				faltante = faltante.subtract(tomar);
			}
		}
		if (faltante.signum() > 0) {
			throw error("Combo sin stock disponible: " + producto.getNombre());
		}
		return consumo;
	}

	private void repartirDescuento(List<Linea> lineas, BigDecimal descuento, BigDecimal subtotal) {
		BigDecimal asignado = BigDecimal.ZERO;
		for (int i = 0; i < lineas.size(); i++) {
			Linea linea = lineas.get(i);
			if (i == lineas.size() - 1) {
				linea.descuento = dinero(descuento.subtract(asignado));
			} else if (subtotal.signum() == 0) {
				linea.descuento = BigDecimal.ZERO.setScale(2);
			} else {
				linea.descuento = dinero(descuento.multiply(linea.subtotal).divide(subtotal, 6, RoundingMode.HALF_UP));
				asignado = asignado.add(linea.descuento);
			}
		}
	}

	private BigDecimal costoUnitario(Linea linea, Map<UUID, Producto> productos) {
		BigDecimal propio = cero(linea.producto.getPrecioCosto());
		if (!linea.producto.isCombo() || propio.signum() > 0) {
			return dinero(propio);
		}
		// Combo sin costo cargado: suma del costo de lo que se desconto
		BigDecimal total = BigDecimal.ZERO;
		for (Map.Entry<UUID, BigDecimal> e : linea.consumo.entrySet()) {
			Producto p = productos.get(e.getKey());
			total = total.add(cero(p == null ? null : p.getPrecioCosto()).multiply(e.getValue()));
		}
		return dinero(total.divide(linea.cantidad, 6, RoundingMode.HALF_UP));
	}

	private static final class Linea {
		private final Producto producto;
		private final BigDecimal cantidad;
		private final List<VentaCheckoutItemRequest> componentes;
		private Map<UUID, BigDecimal> consumo;
		private BigDecimal subtotal;
		private BigDecimal descuento;

		private Linea(Producto producto, BigDecimal cantidad, List<VentaCheckoutItemRequest> componentes) {
			this.producto = producto;
			this.cantidad = cantidad;
			this.componentes = componentes;
		}
	}
}
