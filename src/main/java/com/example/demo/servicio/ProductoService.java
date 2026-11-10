package com.example.demo.servicio;

import static com.example.demo.util.Valores.cero;
import static com.example.demo.util.Valores.conflicto;
import static com.example.demo.util.Valores.error;
import static com.example.demo.util.Valores.noEncontrado;
import static com.example.demo.util.Valores.requerido;
import static com.example.demo.util.Valores.texto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.AjusteStockRequest;
import com.example.demo.dto.ComboRequest;
import com.example.demo.dto.MovimientoStockDto;
import com.example.demo.dto.ProductoDto;
import com.example.demo.dto.ProductoRequest;
import com.example.demo.dto.ProductoVentaDto;
import com.example.demo.enums.TipoMovimientoStock;
import com.example.demo.modelo.Categoria;
import com.example.demo.modelo.ComboItem;
import com.example.demo.modelo.Producto;
import com.example.demo.modelo.Proveedor;
import com.example.demo.modelo.Tipo;
import com.example.demo.modelo.UnidadMedida;
import com.example.demo.repositorio.CategoriaRepository;
import com.example.demo.repositorio.ProductoRepository;
import com.example.demo.repositorio.ProveedorRepository;
import com.example.demo.repositorio.TipoRepository;
import com.example.demo.repositorio.UnidadMedidaRepository;
import com.example.demo.util.Valores;

@Service
public class ProductoService {

	private static final Map<String, String> UNIT_DISPLAY_NAMES = Map.of(
		"UN", "Unidad",
		"KG", "Kilogramo",
		"G", "Gramo",
		"L", "Litro",
		"ML", "Mililitro"
	);

	private final ProductoRepository productoRepository;
	private final CategoriaRepository categoriaRepository;
	private final UnidadMedidaRepository unidadMedidaRepository;
	private final TipoRepository tipoRepository;
	private final ProveedorRepository proveedorRepository;
	private final StockService stockService;

	public ProductoService(
		ProductoRepository productoRepository,
		CategoriaRepository categoriaRepository,
		UnidadMedidaRepository unidadMedidaRepository,
		TipoRepository tipoRepository,
		ProveedorRepository proveedorRepository,
		StockService stockService
	) {
		this.productoRepository = productoRepository;
		this.categoriaRepository = categoriaRepository;
		this.unidadMedidaRepository = unidadMedidaRepository;
		this.tipoRepository = tipoRepository;
		this.proveedorRepository = proveedorRepository;
		this.stockService = stockService;
	}

	// ------------------------------------------------------------------ consultas

	@Transactional(readOnly = true)
	public List<ProductoDto> listar() {
		List<Producto> productos = productoRepository.findAllByOrderByNombreAsc();
		// Una sola consulta de stock para todos los productos
		Map<UUID, BigDecimal> stock = stockService.stockDeTodos();
		Map<UUID, List<Producto>> porCategoria = productos.stream()
			.filter(p -> !p.isCombo() && Boolean.TRUE.equals(p.getActivo()) && p.getCategoria() != null)
			.collect(Collectors.groupingBy(p -> p.getCategoria().getId()));
		Map<UUID, Producto> porId = productos.stream().collect(Collectors.toMap(Producto::getId, p -> p));
		return productos.stream()
			.map(p -> toDto(p, stock, porCategoria, porId))
			.toList();
	}

	@Transactional(readOnly = true)
	public ProductoVentaDto obtenerParaVentaPorCodigo(String barcode) {
		String normalized = normalizeBarcode(barcode);
		Producto producto = productoRepository.findByCodigoBarraAndActivoTrue(normalized)
			.orElseThrow(() -> noEncontrado("Producto no encontrado"));
		if (producto.isCombo() && !comboDisponible(producto)) {
			throw noEncontrado("Producto no encontrado");
		}
		return toVentaDto(producto);
	}

	@Transactional(readOnly = true)
	public List<ProductoVentaDto> buscarParaVentaPorNombre(String query, UUID categoriaId) {
		String normalized = texto(query);
		if (normalized.isEmpty()) {
			return List.of();
		}
		return productoRepository.findTop20ByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(normalized).stream()
			.filter(producto -> categoriaId == null
				|| (producto.getCategoria() != null && categoriaId.equals(producto.getCategoria().getId())))
			.filter(producto -> !producto.isCombo() || comboDisponible(producto))
			.map(this::toVentaDto)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<MovimientoStockDto> movimientos(UUID productoId) {
		return stockService.movimientosDe(productoId).stream()
			.map(m -> new MovimientoStockDto(
				m.getId(),
				m.getFechaHora(),
				m.getTipo(),
				m.getCantidad(),
				m.getMotivo(),
				m.getVenta() != null ? m.getVenta().getNumero() : null
			))
			.toList();
	}

	// ------------------------------------------------------------------ productos

	@Transactional
	public ProductoDto crear(ProductoRequest request) {
		String barcode = normalizeBarcode(request.barcode());
		if (productoRepository.existsByCodigoBarra(barcode)) {
			throw conflicto("Codigo de barra existente");
		}
		Producto producto = new Producto();
		applyCommonFields(producto, barcode, request.name(), request.description(), request.categoryId(),
			request.supplierId(), request.unit(), request.type(), request.cost(), request.price(),
			request.active(), true);
		producto.setStockMinimo(Valores.cantidad(request.minStock()));
		producto.setCombo(false);
		producto.setComboItems(new ArrayList<>());
		Producto saved = productoRepository.save(producto);
		BigDecimal inicial = cero(request.stock());
		if (inicial.signum() > 0) {
			stockService.registrar(saved, TipoMovimientoStock.INICIAL, inicial, "Stock inicial", null, null);
		}
		return dtoDe(saved);
	}

	@Transactional
	public ProductoDto actualizar(UUID id, ProductoRequest request) {
		Producto producto = productoRepository.findById(id)
			.orElseThrow(() -> noEncontrado("Producto no encontrado"));
		if (producto.isCombo()) {
			throw error("El producto es un combo");
		}
		String barcode = normalizeBarcode(request.barcode());
		if (productoRepository.existsByCodigoBarraAndIdNot(barcode, id)) {
			throw conflicto("Codigo de barra existente");
		}
		applyCommonFields(producto, barcode, request.name(), request.description(), request.categoryId(),
			request.supplierId(), request.unit(), request.type(), request.cost(), request.price(),
			request.active(), false);
		producto.setStockMinimo(Valores.cantidad(request.minStock()));
		Producto saved = productoRepository.save(producto);

		// El stock ya no se pisa: si el formulario cambio la cantidad se registra un ajuste
		// solo por la diferencia que hizo el usuario.
		if (request.stock() != null) {
			stockService.bloquear(List.of(id));
			BigDecimal referencia = request.stockOriginal() != null
				? request.stockOriginal()
				: stockService.stockDe(id);
			BigDecimal delta = Valores.cantidad(request.stock().subtract(referencia));
			if (delta.signum() != 0) {
				stockService.registrar(saved, TipoMovimientoStock.AJUSTE, delta, "Ajuste desde edicion de producto", null, null);
			}
		}
		return dtoDe(saved);
	}

	@Transactional
	public ProductoDto ajustarStock(UUID id, AjusteStockRequest request) {
		Producto producto = productoRepository.findById(id)
			.orElseThrow(() -> noEncontrado("Producto no encontrado"));
		if (producto.isCombo()) {
			throw error("Los combos no tienen stock propio");
		}
		stockService.bloquear(List.of(id));
		BigDecimal actual = stockService.stockDe(id);
		BigDecimal delta = Valores.cantidad(request.newStock().subtract(actual));
		if (delta.signum() != 0) {
			String motivo = texto(request.reason());
			stockService.registrar(producto, TipoMovimientoStock.AJUSTE, delta,
				motivo.isEmpty() ? "Ajuste manual" : motivo, null, null);
		}
		return dtoDe(producto);
	}

	@Transactional
	public ProductoDto cambiarEstado(UUID id, boolean active) {
		Producto producto = productoRepository.findById(id)
			.orElseThrow(() -> noEncontrado("Producto no encontrado"));
		producto.setActivo(active);
		return dtoDe(productoRepository.save(producto));
	}

	// ------------------------------------------------------------------ combos

	@Transactional
	public ProductoDto crearCombo(ComboRequest request) {
		String barcode = normalizeOptionalBarcode(request.barcode());
		if (barcode.isEmpty()) {
			barcode = generateComboBarcode();
		}
		if (productoRepository.existsByCodigoBarra(barcode)) {
			throw conflicto("Codigo de barra existente");
		}
		Producto producto = new Producto();
		applyCommonFields(producto, barcode, request.name(), request.description(), request.categoryId(),
			null, request.unit(), request.type(), request.cost(), request.price(), request.active(), true);
		producto.setStockMinimo(BigDecimal.ZERO);
		producto.setCombo(true);
		applyComboDefinition(producto, request, null);
		return dtoDe(productoRepository.save(producto));
	}

	@Transactional
	public ProductoDto actualizarCombo(UUID id, ComboRequest request) {
		Producto producto = productoRepository.findById(id)
			.orElseThrow(() -> noEncontrado("Producto no encontrado"));
		if (!producto.isCombo()) {
			throw error("El producto no es un combo");
		}
		String barcode = normalizeOptionalBarcode(request.barcode());
		if (barcode.isEmpty()) {
			barcode = producto.getCodigoBarra();
		}
		if (productoRepository.existsByCodigoBarraAndIdNot(barcode, id)) {
			throw conflicto("Codigo de barra existente");
		}
		applyCommonFields(producto, barcode, request.name(), request.description(), request.categoryId(),
			null, request.unit(), request.type(), request.cost(), request.price(), request.active(), false);
		applyComboDefinition(producto, request, id);
		return dtoDe(productoRepository.save(producto));
	}

	private void applyComboDefinition(Producto producto, ComboRequest request, UUID comboId) {
		boolean hasGroup = request.groupCategoryId() != null || request.groupQuantity() != null;
		boolean hasItems = request.items() != null && !request.items().isEmpty();
		if (hasGroup && hasItems) {
			throw error("El combo no puede mezclar grupo y productos");
		}
		if (!hasGroup && !hasItems) {
			throw error("Seleccione productos o defina un grupo para el combo");
		}
		if (hasGroup) {
			if (request.groupCategoryId() == null) {
				throw error("Seleccione una categoria para el grupo");
			}
			if (request.groupQuantity() == null || request.groupQuantity() <= 0) {
				throw error("Cantidad invalida para el grupo");
			}
			producto.setComboGrupoCategoria(resolveCategoria(request.groupCategoryId()));
			producto.setComboGrupoCantidad(request.groupQuantity());
			producto.setComboItems(new ArrayList<>());
			return;
		}
		Set<UUID> ids = request.items().stream()
			.filter(Objects::nonNull)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (ids.isEmpty()) {
			throw error("Seleccione al menos un producto para el combo");
		}
		if (comboId != null && ids.contains(comboId)) {
			throw error("El combo no puede incluirse a si mismo");
		}
		List<Producto> items = productoRepository.findAllById(ids);
		if (items.size() != ids.size()) {
			throw error("Algunos productos del combo no existen");
		}
		if (items.stream().anyMatch(Producto::isCombo)) {
			throw error("No se pueden incluir combos dentro de otro combo");
		}
		producto.setComboGrupoCategoria(null);
		producto.setComboGrupoCantidad(null);
		producto.setComboItems(ids.stream().map(itemId -> new ComboItem(itemId, BigDecimal.ONE)).collect(Collectors.toCollection(ArrayList::new)));
	}

	/** Un combo esta disponible si alcanza el stock de sus componentes para venderlo una vez. */
	@Transactional(readOnly = true)
	public boolean comboDisponible(Producto combo) {
		if (combo == null || !combo.isCombo()) {
			return true;
		}
		if (combo.isComboDeGrupo()) {
			List<Producto> productos = productoRepository
				.findByCategoriaIdAndActivoTrueAndComboFalse(combo.getComboGrupoCategoria().getId());
			Map<UUID, BigDecimal> stock = stockService.stockDe(productos.stream().map(Producto::getId).toList());
			BigDecimal total = stock.values().stream().filter(v -> v.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
			return total.compareTo(BigDecimal.valueOf(combo.getComboGrupoCantidad())) >= 0;
		}
		List<ComboItem> items = combo.getComboItems();
		if (items == null || items.isEmpty()) {
			return false;
		}
		List<UUID> ids = items.stream().map(ComboItem::productoId).toList();
		Map<UUID, Producto> productos = productoRepository.findAllById(ids).stream()
			.collect(Collectors.toMap(Producto::getId, p -> p));
		Map<UUID, BigDecimal> stock = stockService.stockDe(ids);
		for (ComboItem item : items) {
			Producto componente = productos.get(item.productoId());
			if (componente == null || !Boolean.TRUE.equals(componente.getActivo())) {
				return false;
			}
			if (cero(stock.get(item.productoId())).compareTo(cero(item.cantidad())) < 0) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ mapeo

	private ProductoDto dtoDe(Producto producto) {
		Set<UUID> ids = new LinkedHashSet<>();
		ids.add(producto.getId());
		Map<UUID, List<Producto>> porCategoria = Map.of();
		if (producto.isComboDeGrupo()) {
			List<Producto> grupo = productoRepository
				.findByCategoriaIdAndActivoTrueAndComboFalse(producto.getComboGrupoCategoria().getId());
			grupo.forEach(p -> ids.add(p.getId()));
			porCategoria = Map.of(producto.getComboGrupoCategoria().getId(), grupo);
		}
		Map<UUID, Producto> porId = new java.util.HashMap<>();
		if (producto.isCombo() && producto.getComboItems() != null) {
			producto.getComboItems().forEach(i -> ids.add(i.productoId()));
			productoRepository.findAllById(producto.getComboItems().stream().map(ComboItem::productoId).toList())
				.forEach(p -> porId.put(p.getId(), p));
		}
		return toDto(producto, stockService.stockDe(ids), porCategoria, porId);
	}

	private ProductoDto toDto(
		Producto producto,
		Map<UUID, BigDecimal> stock,
		Map<UUID, List<Producto>> activosPorCategoria,
		Map<UUID, Producto> porId
	) {
		BigDecimal cantidad;
		BigDecimal minimo = cero(producto.getStockMinimo());
		boolean active = Boolean.TRUE.equals(producto.getActivo());
		if (producto.isCombo()) {
			cantidad = combosArmables(producto, stock, activosPorCategoria, porId);
			active = active && cantidad.signum() > 0;
		} else {
			cantidad = cero(stock.get(producto.getId()));
		}
		List<UUID> comboItems = producto.isCombo() && !producto.isComboDeGrupo() && producto.getComboItems() != null
			? producto.getComboItems().stream().map(ComboItem::productoId).toList()
			: List.of();
		return new ProductoDto(
			producto.getId(),
			producto.getCodigoBarra(),
			producto.getNombre(),
			producto.getDescripcion(),
			producto.getCategoria() != null ? producto.getCategoria().getId() : null,
			producto.getCategoria() != null ? producto.getCategoria().getNombre() : null,
			producto.getProveedor() != null ? producto.getProveedor().getTelefono() : null,
			producto.getProveedor() != null ? producto.getProveedor().getNombre() : null,
			producto.getUnidadMedida() != null ? producto.getUnidadMedida().getAbreviatura() : null,
			producto.getTipo() != null ? producto.getTipo().getNombre() : null,
			cero(producto.getPrecioCosto()),
			cero(producto.getPrecioVenta()),
			cantidad,
			minimo,
			active,
			producto.isCombo(),
			comboItems,
			producto.isComboDeGrupo() ? producto.getComboGrupoCategoria().getId() : null,
			producto.isComboDeGrupo() ? producto.getComboGrupoCategoria().getNombre() : null,
			producto.isComboDeGrupo() ? producto.getComboGrupoCantidad() : null
		);
	}

	/** Cuantos combos se pueden armar con el stock actual. */
	private BigDecimal combosArmables(
		Producto combo,
		Map<UUID, BigDecimal> stock,
		Map<UUID, List<Producto>> activosPorCategoria,
		Map<UUID, Producto> porId
	) {
		if (combo.isComboDeGrupo()) {
			List<Producto> grupo = activosPorCategoria.getOrDefault(combo.getComboGrupoCategoria().getId(), List.of());
			BigDecimal total = grupo.stream()
				.map(p -> cero(stock.get(p.getId())))
				.filter(v -> v.signum() > 0)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
			return total.divide(BigDecimal.valueOf(combo.getComboGrupoCantidad()), 0, RoundingMode.DOWN);
		}
		if (combo.getComboItems() == null || combo.getComboItems().isEmpty()) {
			return BigDecimal.ZERO;
		}
		BigDecimal minimo = null;
		for (ComboItem item : combo.getComboItems()) {
			Producto componente = porId.get(item.productoId());
			if (componente == null || !Boolean.TRUE.equals(componente.getActivo())) {
				return BigDecimal.ZERO;
			}
			BigDecimal porUnidad = cero(item.cantidad()).signum() > 0 ? item.cantidad() : BigDecimal.ONE;
			BigDecimal posibles = cero(stock.get(item.productoId())).divide(porUnidad, 0, RoundingMode.DOWN);
			minimo = minimo == null ? posibles : minimo.min(posibles);
		}
		return minimo == null ? BigDecimal.ZERO : minimo.max(BigDecimal.ZERO);
	}

	private ProductoVentaDto toVentaDto(Producto producto) {
		return new ProductoVentaDto(
			producto.getId(),
			producto.getCodigoBarra(),
			producto.getNombre(),
			cero(producto.getPrecioVenta()),
			producto.getCategoria() != null ? producto.getCategoria().getId() : null,
			producto.getCategoria() != null ? producto.getCategoria().getNombre() : null
		);
	}

	// ------------------------------------------------------------------ auxiliares

	private void applyCommonFields(
		Producto producto,
		String barcode,
		String name,
		String description,
		UUID categoryId,
		String supplierId,
		String unit,
		String type,
		BigDecimal cost,
		BigDecimal price,
		Boolean active,
		boolean creating
	) {
		producto.setCodigoBarra(normalizeBarcode(barcode));
		producto.setNombre(requerido(name, "Nombre requerido"));
		producto.setDescripcion(Valores.textoONull(description));
		producto.setPrecioCosto(Valores.dinero(cost));
		producto.setPrecioVenta(Valores.dinero(price));
		producto.setCategoria(categoryId == null ? null : resolveCategoria(categoryId));
		producto.setProveedor(resolveProveedor(supplierId));
		producto.setUnidadMedida(resolveUnidad(unit));
		producto.setTipo(resolveTipo(type));
		if (creating) {
			producto.setActivo(active == null || active);
		} else if (active != null) {
			producto.setActivo(active);
		}
	}

	private Categoria resolveCategoria(UUID categoryId) {
		Categoria categoria = categoriaRepository.findById(categoryId)
			.orElseThrow(() -> error("Categoria invalida"));
		if (!Boolean.TRUE.equals(categoria.getActiva())) {
			throw error("Categoria inactiva");
		}
		return categoria;
	}

	private Proveedor resolveProveedor(String supplierId) {
		String id = texto(supplierId);
		if (id.isEmpty()) {
			return null;
		}
		Proveedor proveedor = proveedorRepository.findById(id)
			.orElseThrow(() -> error("Proveedor invalido"));
		if (!Boolean.TRUE.equals(proveedor.getActivo())) {
			throw error("Proveedor inactivo");
		}
		return proveedor;
	}

	private UnidadMedida resolveUnidad(String raw) {
		String abreviatura = requerido(raw, "Unidad requerida").toUpperCase(Locale.ROOT);
		return unidadMedidaRepository.findById(abreviatura)
			.orElseGet(() -> {
				UnidadMedida unidad = new UnidadMedida();
				unidad.setAbreviatura(abreviatura);
				unidad.setNombre(UNIT_DISPLAY_NAMES.getOrDefault(abreviatura, abreviatura));
				return unidadMedidaRepository.save(unidad);
			});
	}

	private Tipo resolveTipo(String raw) {
		String nombre = requerido(raw, "Tipo requerido");
		String clave = nombre.toLowerCase(Locale.ROOT);
		return tipoRepository.findById(clave)
			.orElseGet(() -> {
				Tipo tipo = new Tipo();
				tipo.setId(clave);
				tipo.setNombre(nombre);
				return tipoRepository.save(tipo);
			});
	}

	private String normalizeBarcode(String barcode) {
		String normalized = requerido(barcode, "Codigo de barra requerido");
		if (normalized.length() > 64) {
			throw error("Codigo de barra demasiado largo");
		}
		return normalized;
	}

	private String normalizeOptionalBarcode(String barcode) {
		String normalized = texto(barcode);
		if (normalized.length() > 64) {
			throw error("Codigo de barra demasiado largo");
		}
		return normalized;
	}

	private String generateComboBarcode() {
		for (int i = 0; i < 5; i++) {
			String candidate = "COMBO-" + UUID.randomUUID();
			if (!productoRepository.existsByCodigoBarra(candidate)) {
				return candidate;
			}
		}
		throw conflicto("Codigo de barra existente");
	}
}
