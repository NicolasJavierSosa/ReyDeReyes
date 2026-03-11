package com.example.demo.servicio;

import com.example.demo.dto.SolicitudCompraDto;
import com.example.demo.dto.SolicitudCompraItemDto;
import com.example.demo.dto.SolicitudCompraItemRequest;
import com.example.demo.dto.SolicitudCompraRecepcionItemRequest;
import com.example.demo.dto.SolicitudCompraRecepcionRequest;
import com.example.demo.dto.SolicitudCompraRequest;
import com.example.demo.enums.EstadoSolicitudCompra;
import com.example.demo.modelo.Producto;
import com.example.demo.modelo.Proveedor;
import com.example.demo.modelo.SolicitudCompra;
import com.example.demo.modelo.SolicitudCompraItem;
import com.example.demo.modelo.Stock;
import com.example.demo.repositorio.ProductoRepository;
import com.example.demo.repositorio.ProveedorRepository;
import com.example.demo.repositorio.SolicitudCompraRepository;
import com.example.demo.repositorio.StockRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SolicitudCompraService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final SolicitudCompraRepository solicitudCompraRepository;
	private final ProveedorRepository proveedorRepository;
	private final ProductoRepository productoRepository;
	private final StockRepository stockRepository;

	public SolicitudCompraService(
		SolicitudCompraRepository solicitudCompraRepository,
		ProveedorRepository proveedorRepository,
		ProductoRepository productoRepository,
		StockRepository stockRepository
	) {
		this.solicitudCompraRepository = solicitudCompraRepository;
		this.proveedorRepository = proveedorRepository;
		this.productoRepository = productoRepository;
		this.stockRepository = stockRepository;
	}

	@Transactional(readOnly = true)
	public List<SolicitudCompraDto> listar(String status) {
		List<SolicitudCompra> solicitudes;
		if (status != null && status.equalsIgnoreCase("pending")) {
			solicitudes = solicitudCompraRepository.findWithItemsByEstadoIn(List.of(
				EstadoSolicitudCompra.SOLICITADA,
				EstadoSolicitudCompra.PARCIAL
			));
		} else {
			solicitudes = solicitudCompraRepository.findWithItems();
		}
		return solicitudes.stream().map(this::toDto).toList();
	}

	@Transactional
	public SolicitudCompraDto crear(SolicitudCompraRequest request) {
		if (request == null || request.items() == null || request.items().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar productos");
		}

		Proveedor proveedor = proveedorRepository.findById(request.supplierId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
		if (!Boolean.TRUE.equals(proveedor.getActivo())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor inactivo");
		}

		Set<Long> productIds = request.items().stream()
			.map(SolicitudCompraItemRequest::productId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		if (productIds.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar productos");
		}

		Map<Long, Producto> products = productoRepository.findAllById(productIds).stream()
			.collect(Collectors.toMap(Producto::getId, p -> p));

		SolicitudCompra solicitud = new SolicitudCompra();
		solicitud.setProveedor(proveedor);
		solicitud.setEstado(EstadoSolicitudCompra.SOLICITADA);
		solicitud.setFechaHora(LocalDateTime.now());

		List<SolicitudCompraItem> items = request.items().stream().map(item -> {
			Producto producto = products.get(item.productId());
			if (producto == null) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
			}
			BigDecimal qty = scaleQty(item.qty());
			if (qty.compareTo(BigDecimal.ZERO) <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad invalida");
			}
			SolicitudCompraItem entity = new SolicitudCompraItem();
			entity.setSolicitud(solicitud);
			entity.setProducto(producto);
			entity.setCantidadSolicitada(qty);
			entity.setCantidadRecibida(BigDecimal.ZERO);
			return entity;
		}).toList();

		solicitud.setItems(items);
		SolicitudCompra saved = solicitudCompraRepository.save(solicitud);
		return toDto(saved);
	}

	@Transactional
	public SolicitudCompraDto recepcionar(Long id, SolicitudCompraRecepcionRequest request) {
		if (request == null || request.items() == null || request.items().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe informar cantidades recibidas");
		}

		SolicitudCompra solicitud = solicitudCompraRepository.findByIdWithItems(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));

		Map<Long, SolicitudCompraItem> itemsMap = solicitud.getItems().stream()
			.collect(Collectors.toMap(SolicitudCompraItem::getId, item -> item));

		Map<Long, BigDecimal> updates = new HashMap<>();
		for (SolicitudCompraRecepcionItemRequest itemRequest : request.items()) {
			if (itemRequest == null || itemRequest.itemId() == null) continue;
			SolicitudCompraItem item = itemsMap.get(itemRequest.itemId());
			if (item == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item invalido en la solicitud");
			}
			BigDecimal received = scaleQty(itemRequest.receivedQty());
			if (received.compareTo(BigDecimal.ZERO) < 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad recibida invalida");
			}
			updates.put(item.getId(), received);
		}

		for (SolicitudCompraItem item : solicitud.getItems()) {
			if (!updates.containsKey(item.getId())) continue;
			BigDecimal oldReceived = defaultZero(item.getCantidadRecibida());
			BigDecimal newReceived = updates.get(item.getId());
			BigDecimal delta = newReceived.subtract(oldReceived);
			if (delta.compareTo(BigDecimal.ZERO) != 0) {
				applyStockDelta(item.getProducto(), delta);
			}
			item.setCantidadRecibida(newReceived);
		}

		boolean allReceived = solicitud.getItems().stream()
			.allMatch(item -> defaultZero(item.getCantidadRecibida()).compareTo(defaultZero(item.getCantidadSolicitada())) >= 0);
		boolean anyReceived = solicitud.getItems().stream()
			.anyMatch(item -> defaultZero(item.getCantidadRecibida()).compareTo(BigDecimal.ZERO) > 0);

		if (allReceived) {
			solicitud.setEstado(EstadoSolicitudCompra.RECIBIDA);
		} else if (anyReceived) {
			solicitud.setEstado(EstadoSolicitudCompra.PARCIAL);
		} else {
			solicitud.setEstado(EstadoSolicitudCompra.SOLICITADA);
		}

		SolicitudCompra saved = solicitudCompraRepository.save(solicitud);
		return toDto(saved);
	}

	private void applyStockDelta(Producto producto, BigDecimal delta) {
		if (producto == null) return;
		Stock stock = stockRepository.findByProductoId(producto.getId()).orElse(null);
		if (stock == null) {
			stock = new Stock();
			stock.setProducto(producto);
			stock.setCantidadActual(BigDecimal.ZERO);
			stock.setStockMinimo(BigDecimal.ZERO);
		}
		BigDecimal actual = defaultZero(stock.getCantidadActual());
		BigDecimal nuevo = actual.add(delta);
		stock.setCantidadActual(scaleQty(nuevo));
		stock.setUltimoMovimiento(LocalDateTime.now());
		stockRepository.save(stock);
	}

	private SolicitudCompraDto toDto(SolicitudCompra solicitud) {
		if (solicitud == null) return null;
		List<SolicitudCompraItemDto> items = solicitud.getItems() == null
			? List.of()
			: solicitud.getItems().stream().map(item -> new SolicitudCompraItemDto(
				item.getId(),
				item.getProducto() != null ? item.getProducto().getId() : null,
				item.getProducto() != null ? item.getProducto().getNombre() : "",
				scaleQty(item.getCantidadSolicitada()),
				scaleQty(item.getCantidadRecibida())
			)).toList();

		return new SolicitudCompraDto(
			solicitud.getId(),
			solicitud.getProveedor() != null ? solicitud.getProveedor().getTelefono() : "",
			solicitud.getProveedor() != null ? solicitud.getProveedor().getNombre() : "",
			solicitud.getEstado() != null ? solicitud.getEstado().name() : EstadoSolicitudCompra.SOLICITADA.name(),
			solicitud.getFechaHora() != null ? solicitud.getFechaHora().format(DATE_FORMAT) : "",
			items
		);
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private BigDecimal scaleQty(BigDecimal value) {
		return defaultZero(value).setScale(3, RoundingMode.HALF_UP);
	}
}
