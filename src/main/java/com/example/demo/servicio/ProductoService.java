package com.example.demo.servicio;

import com.example.demo.dto.ComboRequest;
import com.example.demo.dto.ProductoDto;
import com.example.demo.dto.ProductoRequest;
import com.example.demo.dto.ProductoVentaDto;
import com.example.demo.modelo.Categoria;
import com.example.demo.modelo.Producto;
import com.example.demo.modelo.Proveedor;
import com.example.demo.modelo.Stock;
import com.example.demo.modelo.Tipo;
import com.example.demo.modelo.UnidadMedida;
import com.example.demo.repositorio.CategoriaRepository;
import com.example.demo.repositorio.ProductoRepository;
import com.example.demo.repositorio.ProveedorRepository;
import com.example.demo.repositorio.StockRepository;
import com.example.demo.repositorio.TipoRepository;
import com.example.demo.repositorio.UnidadMedidaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductoService {

	private static final LocalDate DEFAULT_FECHA_VENCIMIENTO = LocalDate.of(2099, 12, 31);
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
	private final StockRepository stockRepository;

	public ProductoService(
		ProductoRepository productoRepository,
		CategoriaRepository categoriaRepository,
		UnidadMedidaRepository unidadMedidaRepository,
		TipoRepository tipoRepository,
		ProveedorRepository proveedorRepository,
		StockRepository stockRepository
	) {
		this.productoRepository = productoRepository;
		this.categoriaRepository = categoriaRepository;
		this.unidadMedidaRepository = unidadMedidaRepository;
		this.tipoRepository = tipoRepository;
		this.proveedorRepository = proveedorRepository;
		this.stockRepository = stockRepository;
	}

	@Transactional(readOnly = true)
	public List<ProductoDto> listar() {
		return productoRepository.findAllByOrderByNombreAsc().stream()
			.map(this::toDto)
			.toList();
	}

	@Transactional
	public ProductoDto crear(ProductoRequest request) {
		String barcode = normalizeBarcode(request.barcode());
		if (productoRepository.existsByCodigoBarra(barcode)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Codigo de barra existente");
		}

		Producto producto = new Producto();
		applyRequest(producto, request, true);
		producto.setCombo(false);

		Producto saved = productoRepository.save(producto);
		upsertStock(saved, defaultZero(request.stock()), defaultZero(request.minStock()));
		return toDto(saved);
	}

	@Transactional
	public ProductoDto actualizar(Long id, ProductoRequest request) {
		Producto producto = productoRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

		String barcode = normalizeBarcode(request.barcode());
		if (productoRepository.existsByCodigoBarraAndIdNot(barcode, id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Codigo de barra existente");
		}

		applyRequest(producto, request, false);

		Producto saved = productoRepository.save(producto);
		upsertStock(saved, defaultZero(request.stock()), defaultZero(request.minStock()));
		return toDto(saved);
	}

	@Transactional
	public ProductoDto crearCombo(ComboRequest request) {
		String barcode = resolveComboBarcodeForCreate(request.barcode());
		if (productoRepository.existsByCodigoBarra(barcode)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Codigo de barra existente");
		}

		List<Producto> items = resolveComboItemsForRequest(request, null);

		Producto producto = new Producto();
		applyComboRequest(producto, request, true, barcode);
		producto.setCombo(true);
		assignComboItems(producto, items);

		Producto saved = productoRepository.save(producto);
		upsertStock(saved, BigDecimal.ZERO, BigDecimal.ZERO);
		return toDto(saved);
	}

	@Transactional
	public ProductoDto actualizarCombo(Long id, ComboRequest request) {
		Producto producto = productoRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

		if (!producto.isCombo()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto no es un combo");
		}

		String barcode = resolveComboBarcodeForUpdate(request.barcode(), producto.getCodigoBarra());
		if (productoRepository.existsByCodigoBarraAndIdNot(barcode, id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Codigo de barra existente");
		}

		List<Producto> items = resolveComboItemsForRequest(request, id);

		applyComboRequest(producto, request, false, barcode);
		producto.setCombo(true);
		assignComboItems(producto, items);

		Producto saved = productoRepository.save(producto);
		upsertStock(saved, BigDecimal.ZERO, BigDecimal.ZERO);
		return toDto(saved);
	}

	@Transactional
	public ProductoDto cambiarEstado(Long id, boolean active) {
		Producto producto = productoRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
		producto.setActivo(active);
		Producto saved = productoRepository.save(producto);
		return toDto(saved);
	}

	@Transactional(readOnly = true)
	public ProductoVentaDto obtenerParaVentaPorCodigo(String barcode) {
		String normalized = normalizeBarcode(barcode);
		Producto producto = productoRepository.findByCodigoBarraAndActivoTrue(normalized)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
		if (producto.isCombo() && !isComboEnabled(producto)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
		}
		return toVentaDto(producto);
	}

	@Transactional(readOnly = true)
	public List<ProductoVentaDto> buscarParaVentaPorNombre(String query, Long categoriaId) {
		String normalized = normalizeText(query);
		if (normalized.isEmpty()) {
			return List.of();
		}

		return productoRepository.findTop20ByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(normalized).stream()
			.filter(producto -> categoriaId == null
				|| (producto.getCategoria() != null && categoriaId.equals(producto.getCategoria().getId())))
			.filter(producto -> !producto.isCombo() || isComboEnabled(producto))
			.map(this::toVentaDto)
			.toList();
	}

	private void applyRequest(Producto producto, ProductoRequest request, boolean creating) {
		applyCommonFields(
			producto,
			request.barcode(),
			request.name(),
			request.description(),
			request.categoryId(),
			request.supplierId(),
			request.unit(),
			request.type(),
			request.cost(),
			request.price(),
			request.active(),
			creating
		);
	}

	private void applyComboRequest(Producto producto, ComboRequest request, boolean creating, String barcode) {
		applyCommonFields(
			producto,
			barcode,
			request.name(),
			request.description(),
			request.categoryId(),
			null,
			request.unit(),
			request.type(),
			request.cost(),
			request.price(),
			request.active(),
			creating
		);
		applyComboGroupFields(producto, request);
	}

	private void applyCommonFields(
		Producto producto,
		String barcode,
		String name,
		String description,
		Long categoryId,
		String supplierId,
		String unit,
		String type,
		BigDecimal cost,
		BigDecimal price,
		Boolean active,
		boolean creating
	) {
		producto.setCodigoBarra(normalizeBarcode(barcode));
		producto.setNombre(normalizeRequired(name, "Nombre requerido"));
		producto.setDescripcion(normalizeText(description));
		producto.setPrecioCosto(defaultZero(cost));
		producto.setPrecioVenta(defaultZero(price));
		producto.setCategoria(resolveCategoria(categoryId));
		producto.setProveedor(resolveProveedor(supplierId));
		producto.setUnidadMedida(resolveUnidad(unit));
		producto.setTipo(resolveTipo(type));

		if (creating) {
			producto.setActivo(active == null ? true : active);
		} else if (active != null) {
			producto.setActivo(active);
		}

		if (producto.getFechaVencimiento() == null) {
			producto.setFechaVencimiento(DEFAULT_FECHA_VENCIMIENTO);
		}
	}

	private Categoria resolveCategoria(Long categoryId) {
		if (categoryId == null) {
			return null;
		}
		Categoria categoria = categoriaRepository.findById(categoryId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria invalida"));
		if (!Boolean.TRUE.equals(categoria.getActiva())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inactiva");
		}
		return categoria;
	}

	private Proveedor resolveProveedor(String supplierId) {
		if (supplierId == null) {
			return null;
		}
		Proveedor proveedor = proveedorRepository.findById(supplierId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor invalido"));
		if (!Boolean.TRUE.equals(proveedor.getActivo())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor inactivo");
		}
		return proveedor;
	}

	private UnidadMedida resolveUnidad(String raw) {
		String abreviatura = normalizeRequired(raw, "Unidad requerida").toUpperCase(Locale.ROOT);

		return unidadMedidaRepository.findByAbreviaturaIgnoreCase(abreviatura)
			.orElseGet(() -> {
				UnidadMedida unidad = new UnidadMedida();
				unidad.setAbreviatura(abreviatura);
				unidad.setNombre(UNIT_DISPLAY_NAMES.getOrDefault(abreviatura, abreviatura));
				return unidadMedidaRepository.save(unidad);
			});
	}

	private Tipo resolveTipo(String raw) {
		String nombre = normalizeRequired(raw, "Tipo requerido");

		return tipoRepository.findByNombreIgnoreCase(nombre)
			.orElseGet(() -> {
				Tipo tipo = new Tipo();
				tipo.setNombre(nombre);
				tipo.setDescripcion(null);
				return tipoRepository.save(tipo);
			});
	}

	private void upsertStock(Producto producto, BigDecimal cantidad, BigDecimal minimo) {
		Stock stock = stockRepository.findByProductoId(producto.getId())
			.orElseGet(() -> {
				Stock nuevo = new Stock();
				nuevo.setProducto(producto);
				return nuevo;
			});

		stock.setCantidadActual(cantidad);
		stock.setStockMinimo(minimo);
		stock.setUltimoMovimiento(LocalDateTime.now());
		stockRepository.save(stock);
	}

	private ProductoDto toDto(Producto producto) {
		BigDecimal cantidadActual = BigDecimal.ZERO;
		BigDecimal minimo = BigDecimal.ZERO;

		if (producto.isCombo()) {
			ComboStock comboStock = computeComboStock(producto);
			cantidadActual = comboStock.cantidad();
			minimo = comboStock.minimo();
		} else {
			Stock stock = stockRepository.findByProductoId(producto.getId()).orElse(null);
			cantidadActual = stock != null && stock.getCantidadActual() != null
				? stock.getCantidadActual()
				: BigDecimal.ZERO;
			minimo = stock != null && stock.getStockMinimo() != null
				? stock.getStockMinimo()
				: BigDecimal.ZERO;
		}

		boolean active = Boolean.TRUE.equals(producto.getActivo());
		if (producto.isCombo()) {
			active = active && isComboEnabled(producto);
		}

		Long comboGroupCategoryId = null;
		String comboGroupCategoryName = null;
		Integer comboGroupQuantity = null;
		if (producto.isCombo() && producto.getComboGrupoCategoria() != null) {
			comboGroupCategoryId = producto.getComboGrupoCategoria().getId();
			comboGroupCategoryName = producto.getComboGrupoCategoria().getNombre();
			comboGroupQuantity = producto.getComboGrupoCantidad();
		}

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
			defaultZero(producto.getPrecioCosto()),
			defaultZero(producto.getPrecioVenta()),
			cantidadActual,
			minimo,
			active,
			producto.isCombo(),
			comboItemIds(producto),
			comboGroupCategoryId,
			comboGroupCategoryName,
			comboGroupQuantity
		);
	}

	private ProductoVentaDto toVentaDto(Producto producto) {
		return new ProductoVentaDto(
			producto.getId(),
			producto.getCodigoBarra(),
			producto.getNombre(),
			defaultZero(producto.getPrecioVenta()),
			producto.getCategoria() != null ? producto.getCategoria().getId() : null,
			producto.getCategoria() != null ? producto.getCategoria().getNombre() : null
		);
	}

	private String normalizeBarcode(String barcode) {
		String normalized = normalizeRequired(barcode, "Codigo de barra requerido");
		if (normalized.length() > 64) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codigo de barra demasiado largo");
		}
		return normalized;
	}

	private String resolveComboBarcodeForCreate(String barcode) {
		String normalized = normalizeOptionalBarcode(barcode);
		if (normalized.isEmpty()) {
			return generateComboBarcode();
		}
		return normalized;
	}

	private String resolveComboBarcodeForUpdate(String barcode, String fallback) {
		String normalized = normalizeOptionalBarcode(barcode);
		if (normalized.isEmpty()) {
			if (fallback == null || fallback.isBlank()) {
				return generateComboBarcode();
			}
			return fallback;
		}
		return normalized;
	}

	private String normalizeOptionalBarcode(String barcode) {
		String normalized = normalizeText(barcode);
		if (normalized.length() > 64) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codigo de barra demasiado largo");
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
		throw new ResponseStatusException(HttpStatus.CONFLICT, "Codigo de barra existente");
	}

	private String normalizeRequired(String value, String message) {
		String normalized = normalizeText(value);
		if (normalized.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		}
		return normalized;
	}

	private String normalizeText(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private List<Long> comboItemIds(Producto producto) {
		if (producto == null || !producto.isCombo()) {
			return List.of();
		}
		if (isGroupCombo(producto)) {
			return List.of();
		}
		List<Producto> items = producto.getComboProductos();
		if (items == null || items.isEmpty()) {
			return List.of();
		}
		return items.stream()
			.filter(Objects::nonNull)
			.map(Producto::getId)
			.filter(Objects::nonNull)
			.toList();
	}

	private List<Producto> resolveComboItemsForRequest(ComboRequest request, Long comboId) {
		boolean hasGroup = request.groupCategoryId() != null || request.groupQuantity() != null;
		boolean hasItems = request.items() != null && !request.items().isEmpty();
		if (hasGroup && hasItems) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El combo no puede mezclar grupo y productos");
		}
		if (!hasGroup && !hasItems) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione productos o defina un grupo para el combo");
		}
		if (hasGroup) {
			validateGroupRequest(request);
			return new ArrayList<>();
		}
		return resolveComboItems(request.items(), comboId);
	}

	private void assignComboItems(Producto producto, List<Producto> items) {
		if (producto.getComboProductos() == null) {
			producto.setComboProductos(new ArrayList<>());
		}
		producto.getComboProductos().clear();
		if (items != null && !items.isEmpty()) {
			producto.getComboProductos().addAll(items);
		}
	}

	private List<Producto> resolveComboItems(List<Long> rawIds, Long comboId) {
		if (rawIds == null || rawIds.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione al menos un producto para el combo");
		}

		List<Long> ids = rawIds.stream()
			.filter(Objects::nonNull)
			.distinct()
			.toList();

		if (ids.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione al menos un producto para el combo");
		}

		if (comboId != null && ids.contains(comboId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El combo no puede incluirse a si mismo");
		}

		List<Producto> items = productoRepository.findAllById(ids);
		Set<Long> foundIds = items.stream()
			.map(Producto::getId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		if (foundIds.size() != ids.size()) {
			List<Long> missing = ids.stream()
				.filter(id -> !foundIds.contains(id))
				.toList();
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Productos invalidos: " + missing);
		}

		boolean hasCombo = items.stream().anyMatch(Producto::isCombo);
		if (hasCombo) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pueden incluir combos dentro de otro combo");
		}

		return items;
	}

	private ComboStock computeComboStock(Producto combo) {
		if (combo == null || !combo.isCombo()) {
			return new ComboStock(BigDecimal.ZERO, BigDecimal.ZERO);
		}
		if (isGroupCombo(combo)) {
			BigDecimal totalStock = sumStockByCategory(combo.getComboGrupoCategoria());
			BigDecimal qty = BigDecimal.valueOf(combo.getComboGrupoCantidad());
			if (qty.compareTo(BigDecimal.ZERO) <= 0) {
				return new ComboStock(BigDecimal.ZERO, BigDecimal.ZERO);
			}
			BigDecimal combos = totalStock.divide(qty, 0, RoundingMode.DOWN);
			return new ComboStock(combos, BigDecimal.ZERO);
		}

		List<Producto> items = combo.getComboProductos();
		if (items == null || items.isEmpty()) {
			return new ComboStock(BigDecimal.ZERO, BigDecimal.ZERO);
		}

		BigDecimal minStock = null;
		BigDecimal minMinimo = null;

		for (Producto item : items) {
			if (item == null || item.getId() == null) {
				continue;
			}
			Stock stock = stockRepository.findByProductoId(item.getId()).orElse(null);
			BigDecimal actual = stock != null && stock.getCantidadActual() != null
				? stock.getCantidadActual()
				: BigDecimal.ZERO;
			BigDecimal minimo = stock != null && stock.getStockMinimo() != null
				? stock.getStockMinimo()
				: BigDecimal.ZERO;

			minStock = minStock == null ? actual : minStock.min(actual);
			minMinimo = minMinimo == null ? minimo : minMinimo.min(minimo);
		}

		return new ComboStock(
			minStock == null ? BigDecimal.ZERO : minStock,
			minMinimo == null ? BigDecimal.ZERO : minMinimo
		);
	}

	private boolean isComboEnabled(Producto combo) {
		if (combo == null || !combo.isCombo()) {
			return true;
		}
		if (isGroupCombo(combo)) {
			BigDecimal totalStock = sumStockByCategory(combo.getComboGrupoCategoria());
			int required = combo.getComboGrupoCantidad() != null ? combo.getComboGrupoCantidad() : 0;
			return required > 0 && totalStock.compareTo(BigDecimal.valueOf(required)) >= 0;
		}

		List<Producto> items = combo.getComboProductos();
		if (items == null || items.isEmpty()) {
			return false;
		}

		for (Producto item : items) {
			if (item == null || !Boolean.TRUE.equals(item.getActivo())) {
				return false;
			}
			Stock stock = stockRepository.findByProductoId(item.getId()).orElse(null);
			BigDecimal actual = stock != null && stock.getCantidadActual() != null
				? stock.getCantidadActual()
				: BigDecimal.ZERO;
			if (actual.compareTo(BigDecimal.ZERO) <= 0) {
				return false;
			}
		}

		return true;
	}

	private void applyComboGroupFields(Producto producto, ComboRequest request) {
		Long groupCategoryId = request.groupCategoryId();
		Integer groupQuantity = request.groupQuantity();
		if (groupCategoryId == null && groupQuantity == null) {
			producto.setComboGrupoCategoria(null);
			producto.setComboGrupoCantidad(null);
			return;
		}
		validateGroupRequest(request);
		producto.setComboGrupoCategoria(resolveCategoria(groupCategoryId));
		producto.setComboGrupoCantidad(groupQuantity);
	}

	private void validateGroupRequest(ComboRequest request) {
		if (request.groupCategoryId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione una categoria para el grupo");
		}
		if (request.groupQuantity() == null || request.groupQuantity() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad invalida para el grupo");
		}
	}

	private boolean isGroupCombo(Producto combo) {
		return combo.getComboGrupoCategoria() != null
			&& combo.getComboGrupoCantidad() != null
			&& combo.getComboGrupoCantidad() > 0;
	}

	private BigDecimal sumStockByCategory(Categoria categoria) {
		if (categoria == null || categoria.getId() == null) {
			return BigDecimal.ZERO;
		}
		List<Producto> productos = productoRepository.findByCategoriaIdAndActivoTrue(categoria.getId());
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

	private record ComboStock(BigDecimal cantidad, BigDecimal minimo) {}
}
