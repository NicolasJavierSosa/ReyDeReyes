package com.example.demo.servicio;

import com.example.demo.dto.EstadisticasCategoriaDto;
import com.example.demo.dto.EstadisticasKpiDto;
import com.example.demo.dto.EstadisticasResponse;
import com.example.demo.dto.EstadisticasSerieDto;
import com.example.demo.dto.EstadisticasTrendDto;
import com.example.demo.enums.EstadoVenta;
import com.example.demo.modelo.Categoria;
import com.example.demo.modelo.DetalleVenta;
import com.example.demo.modelo.Producto;
import com.example.demo.modelo.Stock;
import com.example.demo.modelo.Venta;
import com.example.demo.repositorio.StockRepository;
import com.example.demo.repositorio.VentaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstadisticasService {

	private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("es-AR"));

	private final VentaRepository ventaRepository;
	private final StockRepository stockRepository;

	public EstadisticasService(VentaRepository ventaRepository, StockRepository stockRepository) {
		this.ventaRepository = ventaRepository;
		this.stockRepository = stockRepository;
	}

	@Transactional(readOnly = true)
	public EstadisticasResponse obtener(String period) {
		String normalized = normalizePeriod(period);
		LocalDateTime now = LocalDateTime.now();
		Range current = resolveRange(normalized, now);
		Range previous = resolvePreviousRange(normalized, current);

		List<Venta> ventas = ventaRepository.findWithDetailsByEstadoAndFechaHoraBetween(
			EstadoVenta.COMPLETADA,
			current.start(),
			current.end()
		);
		List<Venta> ventasPrev = ventaRepository.findWithDetailsByEstadoAndFechaHoraBetween(
			EstadoVenta.COMPLETADA,
			previous.start(),
			previous.end()
		);

		Metrics metrics = computeMetrics(ventas);
		Metrics prev = computeMetrics(ventasPrev);

		EstadisticasTrendDto trends = new EstadisticasTrendDto(
			trend(metrics.revenue(), prev.revenue()),
			trend(metrics.profit(), prev.profit()),
			trend(metrics.items(), prev.items()),
			trend(BigDecimal.valueOf(metrics.salesCount()), BigDecimal.valueOf(prev.salesCount()))
		);

		EstadisticasKpiDto kpis = new EstadisticasKpiDto(
			money(metrics.revenue()),
			money(metrics.profit()),
			qty(metrics.items()),
			metrics.salesCount(),
			trends
		);

		EstadisticasSerieDto ticketPromedio = buildTicketPromedio(ventas, current.start().toLocalDate(), current.end().toLocalDate(), normalized);
		EstadisticasSerieDto topProducts = buildTopProducts(ventas);
		List<EstadisticasCategoriaDto> categories = buildCategorias(ventas);
		EstadisticasSerieDto rotation = buildRotacion(ventas);

		return new EstadisticasResponse(
			normalized,
			current.start().toLocalDate().toString(),
			current.end().toLocalDate().toString(),
			kpis,
			ticketPromedio,
			topProducts,
			rotation,
			categories
		);
	}

	private Metrics computeMetrics(List<Venta> ventas) {
		BigDecimal revenue = BigDecimal.ZERO;
		BigDecimal profit = BigDecimal.ZERO;
		BigDecimal items = BigDecimal.ZERO;
		int salesCount = ventas != null ? ventas.size() : 0;

		if (ventas != null) {
			for (Venta venta : ventas) {
				if (venta == null) continue;
				revenue = revenue.add(defaultZero(venta.getTotal()));
				if (venta.getDetalles() == null) continue;
				for (DetalleVenta detalle : venta.getDetalles()) {
					if (detalle == null) continue;
					BigDecimal qty = defaultZero(detalle.getCantidad());
					items = items.add(qty);
					BigDecimal linea = defaultZero(detalle.getTotalLinea());
					BigDecimal costo = BigDecimal.ZERO;
					Producto producto = detalle.getProducto();
					if (producto != null) {
						BigDecimal costoUnit = defaultZero(producto.getPrecioCosto());
						costo = costoUnit.multiply(qty);
					}
					profit = profit.add(linea.subtract(costo));
				}
			}
		}

		return new Metrics(revenue, profit, items, salesCount);
	}

	private EstadisticasSerieDto buildTicketPromedio(List<Venta> ventas, LocalDate from, LocalDate to, String period) {
		Map<LocalDate, SumCount> byDate = new HashMap<>();
		if (ventas != null) {
			for (Venta venta : ventas) {
				if (venta == null || venta.getFechaHora() == null) continue;
				LocalDate date = venta.getFechaHora().toLocalDate();
				SumCount agg = byDate.computeIfAbsent(date, key -> new SumCount());
				agg.sum = agg.sum.add(defaultZero(venta.getTotal()));
				agg.count += 1;
			}
		}

		List<String> labels = new ArrayList<>();
		List<BigDecimal> data = new ArrayList<>();
		List<LocalDate> days = buildDates(from, to);
		boolean single = days.size() <= 1;
		for (LocalDate day : days) {
			SumCount agg = byDate.getOrDefault(day, new SumCount());
			BigDecimal avg = agg.count == 0
				? BigDecimal.ZERO
				: agg.sum.divide(BigDecimal.valueOf(agg.count), 2, RoundingMode.HALF_UP);
			String label = single && "hoy".equals(period) ? "Hoy" : day.format(LABEL_FORMAT);
			labels.add(label);
			data.add(avg);
		}

		return new EstadisticasSerieDto(labels, data);
	}

	private EstadisticasSerieDto buildTopProducts(List<Venta> ventas) {
		Map<Long, ProductAgg> byProduct = aggregateByProduct(ventas);
		List<ProductAgg> sorted = byProduct.values().stream()
			.sorted(Comparator.comparing((ProductAgg agg) -> agg.qty).reversed())
			.limit(5)
			.toList();

		List<String> labels = sorted.stream().map(p -> p.name).toList();
		List<BigDecimal> data = sorted.stream().map(p -> qty(p.qty)).toList();

		return new EstadisticasSerieDto(labels, data);
	}

	private EstadisticasSerieDto buildRotacion(List<Venta> ventas) {
		Map<Long, ProductAgg> byProduct = aggregateByProduct(ventas);
		if (byProduct.isEmpty()) {
			return new EstadisticasSerieDto(List.of(), List.of());
		}

		Set<Long> ids = byProduct.keySet();
		Map<Long, BigDecimal> stockMap = stockRepository.findByProductoIdIn(ids).stream()
			.filter(Objects::nonNull)
			.collect(Collectors.toMap(
				stock -> stock.getProducto().getId(),
				stock -> defaultZero(stock.getCantidadActual()),
				(a, b) -> a
			));

		List<ProductAgg> rotationList = new ArrayList<>();
		for (ProductAgg agg : byProduct.values()) {
			BigDecimal stock = stockMap.getOrDefault(agg.id, BigDecimal.ZERO);
			BigDecimal divisor = stock.compareTo(BigDecimal.ZERO) > 0 ? stock : BigDecimal.ONE;
			BigDecimal rotation = agg.qty.divide(divisor, 2, RoundingMode.HALF_UP);
			rotationList.add(agg.withRotation(rotation));
		}

		List<ProductAgg> sorted = rotationList.stream()
			.sorted(Comparator.comparing((ProductAgg agg) -> agg.rotation).reversed())
			.limit(10)
			.toList();

		List<String> labels = sorted.stream().map(p -> p.name).toList();
		List<BigDecimal> data = sorted.stream().map(p -> p.rotation).toList();

		return new EstadisticasSerieDto(labels, data);
	}

	private List<EstadisticasCategoriaDto> buildCategorias(List<Venta> ventas) {
		Map<String, CategoryAgg> byCategory = new HashMap<>();
		if (ventas != null) {
			for (Venta venta : ventas) {
				if (venta == null || venta.getDetalles() == null) continue;
				for (DetalleVenta detalle : venta.getDetalles()) {
					if (detalle == null) continue;
					Producto producto = detalle.getProducto();
					Categoria categoria = producto != null ? producto.getCategoria() : null;
					String name = categoria != null ? categoria.getNombre() : "Sin categoria";
					CategoryAgg agg = byCategory.computeIfAbsent(name, key -> new CategoryAgg(name));
					BigDecimal qty = defaultZero(detalle.getCantidad());
					agg.qty = agg.qty.add(qty);
					BigDecimal revenue = defaultZero(detalle.getTotalLinea());
					agg.revenue = agg.revenue.add(revenue);
					BigDecimal cost = BigDecimal.ZERO;
					if (producto != null) {
						cost = defaultZero(producto.getPrecioCosto()).multiply(qty);
					}
					agg.cost = agg.cost.add(cost);
				}
			}
		}

		return byCategory.values().stream()
			.sorted(Comparator.comparing((CategoryAgg agg) -> agg.revenue).reversed())
			.map(agg -> {
				BigDecimal margin = BigDecimal.ZERO;
				if (agg.revenue.compareTo(BigDecimal.ZERO) > 0) {
					margin = agg.revenue.subtract(agg.cost)
						.divide(agg.revenue, 4, RoundingMode.HALF_UP)
						.multiply(BigDecimal.valueOf(100));
				}
				return new EstadisticasCategoriaDto(
					agg.name,
					qty(agg.qty),
					money(agg.revenue),
					money(agg.cost),
					margin.setScale(2, RoundingMode.HALF_UP)
				);
			})
			.toList();
	}

	private Map<Long, ProductAgg> aggregateByProduct(List<Venta> ventas) {
		Map<Long, ProductAgg> byProduct = new HashMap<>();
		if (ventas != null) {
			for (Venta venta : ventas) {
				if (venta == null || venta.getDetalles() == null) continue;
				for (DetalleVenta detalle : venta.getDetalles()) {
					if (detalle == null) continue;
					Producto producto = detalle.getProducto();
					if (producto == null) continue;
					ProductAgg agg = byProduct.computeIfAbsent(producto.getId(), id -> new ProductAgg(id, producto.getNombre()));
					agg.qty = agg.qty.add(defaultZero(detalle.getCantidad()));
				}
			}
		}
		return byProduct;
	}

	private List<LocalDate> buildDates(LocalDate from, LocalDate to) {
		List<LocalDate> days = new ArrayList<>();
		LocalDate cursor = from;
		while (!cursor.isAfter(to)) {
			days.add(cursor);
			cursor = cursor.plusDays(1);
		}
		return days;
	}

	private Range resolveRange(String period, LocalDateTime now) {
		LocalDate today = now.toLocalDate();
		return switch (period) {
			case "hoy" -> new Range(today.atStartOfDay(), now);
			case "mes" -> new Range(today.withDayOfMonth(1).atStartOfDay(), now);
			default -> {
				LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
				yield new Range(weekStart.atStartOfDay(), now);
			}
		};
	}

	private Range resolvePreviousRange(String period, Range current) {
		return switch (period) {
			case "hoy" -> {
				LocalDateTime end = current.start().minusNanos(1);
				yield new Range(current.start().minusDays(1), end);
			}
			case "mes" -> {
				LocalDateTime end = current.start().minusNanos(1);
				yield new Range(current.start().minusMonths(1), end);
			}
			default -> {
				LocalDateTime end = current.start().minusNanos(1);
				yield new Range(current.start().minusWeeks(1), end);
			}
		};
	}

	private String normalizePeriod(String period) {
		if (period == null) return "semana";
		String value = period.trim().toLowerCase();
		if (value.equals("hoy") || value.equals("semana") || value.equals("mes")) {
			return value;
		}
		return "semana";
	}

	private String trend(BigDecimal current, BigDecimal previous) {
		BigDecimal cur = defaultZero(current);
		BigDecimal prev = defaultZero(previous);
		if (prev.compareTo(BigDecimal.ZERO) == 0) {
			if (cur.compareTo(BigDecimal.ZERO) == 0) {
				return "0%";
			}
			return "+100%";
		}
		BigDecimal pct = cur.subtract(prev)
			.divide(prev, 4, RoundingMode.HALF_UP)
			.multiply(BigDecimal.valueOf(100));
		String sign = pct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
		return sign + pct.setScale(0, RoundingMode.HALF_UP) + "%";
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private BigDecimal money(BigDecimal value) {
		return defaultZero(value).setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal qty(BigDecimal value) {
		return defaultZero(value).setScale(3, RoundingMode.HALF_UP);
	}

	private record Range(LocalDateTime start, LocalDateTime end) {}

	private static class SumCount {
		private BigDecimal sum = BigDecimal.ZERO;
		private int count = 0;
	}

	private record Metrics(BigDecimal revenue, BigDecimal profit, BigDecimal items, int salesCount) {}

	private static class CategoryAgg {
		private final String name;
		private BigDecimal qty = BigDecimal.ZERO;
		private BigDecimal revenue = BigDecimal.ZERO;
		private BigDecimal cost = BigDecimal.ZERO;

		private CategoryAgg(String name) {
			this.name = name;
		}
	}

	private static class ProductAgg {
		private final Long id;
		private final String name;
		private BigDecimal qty = BigDecimal.ZERO;
		private BigDecimal rotation = BigDecimal.ZERO;

		private ProductAgg(Long id, String name) {
			this.id = id;
			this.name = name;
		}

		private ProductAgg withRotation(BigDecimal value) {
			this.rotation = value;
			return this;
		}
	}
}
