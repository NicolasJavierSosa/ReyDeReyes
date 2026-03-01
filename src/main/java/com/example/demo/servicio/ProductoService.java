package com.example.demo.servicio;

import com.example.demo.dto.ProductoDto;
import com.example.demo.dto.ProductoRequest;
import com.example.demo.dto.ProductoVentaDto;
import com.example.demo.modelo.Categoria;
import com.example.demo.modelo.Producto;
import com.example.demo.modelo.Stock;
import com.example.demo.modelo.Tipo;
import com.example.demo.modelo.UnidadMedida;
import com.example.demo.repositorio.CategoriaRepository;
import com.example.demo.repositorio.ProductoRepository;
import com.example.demo.repositorio.StockRepository;
import com.example.demo.repositorio.TipoRepository;
import com.example.demo.repositorio.UnidadMedidaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
	private final StockRepository stockRepository;

	public ProductoService(
		ProductoRepository productoRepository,
		CategoriaRepository categoriaRepository,
		UnidadMedidaRepository unidadMedidaRepository,
		TipoRepository tipoRepository,
		StockRepository stockRepository
	) {
		this.productoRepository = productoRepository;
		this.categoriaRepository = categoriaRepository;
		this.unidadMedidaRepository = unidadMedidaRepository;
		this.tipoRepository = tipoRepository;
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
			.map(this::toVentaDto)
			.toList();
	}

	private void applyRequest(Producto producto, ProductoRequest request, boolean creating) {
		producto.setCodigoBarra(normalizeBarcode(request.barcode()));
		producto.setNombre(normalizeRequired(request.name(), "Nombre requerido"));
		producto.setDescripcion(normalizeText(request.description()));
		producto.setPrecioCosto(defaultZero(request.cost()));
		producto.setPrecioVenta(defaultZero(request.price()));
		producto.setCategoria(resolveCategoria(request.categoryId()));
		producto.setUnidadMedida(resolveUnidad(request.unit()));
		producto.setTipo(resolveTipo(request.type()));

		if (creating) {
			producto.setActivo(request.active() == null ? true : request.active());
		} else if (request.active() != null) {
			producto.setActivo(request.active());
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
		Stock stock = stockRepository.findByProductoId(producto.getId()).orElse(null);
		BigDecimal cantidadActual = stock != null && stock.getCantidadActual() != null
			? stock.getCantidadActual()
			: BigDecimal.ZERO;
		BigDecimal minimo = stock != null && stock.getStockMinimo() != null
			? stock.getStockMinimo()
			: BigDecimal.ZERO;

		return new ProductoDto(
			producto.getId(),
			producto.getCodigoBarra(),
			producto.getNombre(),
			producto.getDescripcion(),
			producto.getCategoria() != null ? producto.getCategoria().getId() : null,
			producto.getCategoria() != null ? producto.getCategoria().getNombre() : null,
			producto.getUnidadMedida() != null ? producto.getUnidadMedida().getAbreviatura() : null,
			producto.getTipo() != null ? producto.getTipo().getNombre() : null,
			defaultZero(producto.getPrecioCosto()),
			defaultZero(producto.getPrecioVenta()),
			cantidadActual,
			minimo,
			Boolean.TRUE.equals(producto.getActivo())
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
}
