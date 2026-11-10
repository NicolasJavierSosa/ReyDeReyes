package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.AjusteStockRequest;
import com.example.demo.dto.AperturaCajaRequest;
import com.example.demo.dto.CategoriaRequest;
import com.example.demo.dto.CierreCajaRequest;
import com.example.demo.dto.ComboRequest;
import com.example.demo.dto.ConteoBilleteRequest;
import com.example.demo.dto.MovimientoManualRequest;
import com.example.demo.dto.ProductoDto;
import com.example.demo.dto.ProductoRequest;
import com.example.demo.dto.SolicitudCompraItemRequest;
import com.example.demo.dto.SolicitudCompraRecepcionItemRequest;
import com.example.demo.dto.SolicitudCompraRecepcionRequest;
import com.example.demo.dto.SolicitudCompraRequest;
import com.example.demo.dto.ProveedorRequest;
import com.example.demo.dto.VentaCheckoutItemRequest;
import com.example.demo.dto.VentaCheckoutPagoRequest;
import com.example.demo.dto.VentaCheckoutRequest;
import com.example.demo.dto.VentaCheckoutResponse;
import com.example.demo.enums.TipoMovimientoTesoreria;
import com.example.demo.servicio.CategoriaService;
import com.example.demo.servicio.EstadisticasService;
import com.example.demo.servicio.ProductoService;
import com.example.demo.servicio.ProveedorService;
import com.example.demo.servicio.SolicitudCompraService;
import com.example.demo.servicio.TesoreriaService;
import com.example.demo.servicio.VentaService;

@EnabledIfEnvironmentVariable(named = "REYDEREYES_TEST_DB_URL", matches = ".+")
class VentaYStockTest extends BaseIntegracionTest {

	@Autowired ProductoService productos;
	@Autowired CategoriaService categorias;
	@Autowired ProveedorService proveedores;
	@Autowired VentaService ventas;
	@Autowired TesoreriaService tesoreria;
	@Autowired SolicitudCompraService compras;
	@Autowired EstadisticasService estadisticas;

	private UUID categoria(String nombre) {
		return categorias.crear(new CategoriaRequest(nombre, null)).getId();
	}

	private ProductoDto producto(String barcode, String nombre, UUID categoria, String costo, String precio, String stock) {
		return productos.crear(new ProductoRequest(barcode, nombre, null, categoria, null, "UN", "General",
			new BigDecimal(costo), new BigDecimal(precio), new BigDecimal(stock), null, BigDecimal.ONE, true));
	}

	private BigDecimal stockDe(String barcode) {
		return productos.listar().stream().filter(p -> p.barcode().equals(barcode)).findFirst().orElseThrow().stock();
	}

	private VentaCheckoutResponse vender(String metodo, String entregado, String descuento, VentaCheckoutItemRequest... items) {
		return ventas.checkout(new VentaCheckoutRequest(List.of(items),
			descuento == null ? null : new BigDecimal(descuento),
			new VentaCheckoutPagoRequest(metodo, entregado == null ? null : new BigDecimal(entregado))));
	}

	private static VentaCheckoutItemRequest item(String barcode, String qty) {
		return new VentaCheckoutItemRequest(barcode, new BigDecimal(qty), null);
	}

	private void abrirCaja(String monto) {
		tesoreria.abrirCaja(new AperturaCajaRequest(new BigDecimal(monto), null));
	}

	@Test
	void sinCajaAbiertaNoSePuedeVender() {
		producto("A1", "Agua", null, "100", "200", "5");
		assertThatThrownBy(() -> vender("EFECTIVO", "200", null, item("A1", "1")))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("No hay caja abierta");
	}

	@Test
	void ventaDescuentaStockRepartiDescuentoYRegistraTesoreria() {
		producto("A1", "Agua", null, "100", "300", "10");
		producto("G1", "Galletas", null, "200", "700", "10");
		abrirCaja("1000");

		VentaCheckoutResponse venta = vender("EFECTIVO", "5000", "100", item("A1", "2"), item("G1", "2"));

		assertThat(venta.subtotal()).isEqualByComparingTo("2000");
		assertThat(venta.total()).isEqualByComparingTo("1900");
		assertThat(venta.change()).isEqualByComparingTo("3100");
		assertThat(venta.ticket()).isEqualTo("000001");
		assertThat(stockDe("A1")).isEqualByComparingTo("8");
		assertThat(stockDe("G1")).isEqualByComparingTo("8");
		// El descuento se reparte proporcional al subtotal de cada linea (600/2000 y 1400/2000)
		List<BigDecimal> descuentos = jdbc.queryForList(
			"select descuento_linea from detalles_venta order by subtotal", BigDecimal.class);
		assertThat(descuentos).usingElementComparator(BigDecimal::compareTo)
			.containsExactly(new BigDecimal("30.00"), new BigDecimal("70.00"));
		// El costo queda guardado en la venta
		assertThat(jdbc.queryForObject("select sum(costo_unitario * cantidad) from detalles_venta", BigDecimal.class))
			.isEqualByComparingTo("600");
		var resumen = tesoreria.obtenerResumen();
		assertThat(resumen.sales()).isEqualByComparingTo("1900");
		assertThat(resumen.cashInDrawer()).isEqualByComparingTo("2900");
	}

	@Test
	void stockInsuficienteRechazaLaVentaSinTocarNada() {
		producto("A1", "Agua", null, "100", "300", "1");
		abrirCaja("0");
		assertThatThrownBy(() -> vender("EFECTIVO", "1000", null, item("A1", "2")))
			.hasMessageContaining("Stock insuficiente");
		assertThat(stockDe("A1")).isEqualByComparingTo("1");
		assertThat(jdbc.queryForObject("select count(*) from ventas", Long.class)).isZero();
	}

	@Test
	void otrosMetodosDePagoNoSumanAlEfectivo() {
		producto("A1", "Agua", null, "100", "300", "10");
		abrirCaja("500");
		vender("TARJETA", null, null, item("A1", "1"));
		vender("QR", null, null, item("A1", "1"));
		var resumen = tesoreria.obtenerResumen();
		assertThat(resumen.sales()).isEqualByComparingTo("600");
		assertThat(resumen.cashInDrawer()).isEqualByComparingTo("500");
		assertThat(resumen.transfersDebited()).isEqualByComparingTo("600");
	}

	@Test
	void comboFijoDescuentaSusComponentes() {
		ProductoDto pan = producto("P1", "Pan", null, "50", "100", "5");
		ProductoDto queso = producto("Q1", "Queso", null, "150", "400", "3");
		productos.crearCombo(new ComboRequest("C1", "Sandwich", null, null, "UN", "Combo",
			BigDecimal.ZERO, new BigDecimal("450"), null, null, true, List.of(pan.id(), queso.id()), null, null));
		abrirCaja("0");

		assertThat(stockDe("C1")).isEqualByComparingTo("3");
		vender("EFECTIVO", "1000", null, item("C1", "2"));

		assertThat(stockDe("P1")).isEqualByComparingTo("3");
		assertThat(stockDe("Q1")).isEqualByComparingTo("1");
		assertThat(stockDe("C1")).isEqualByComparingTo("1");
		// Costo del combo = suma de los componentes (no tenia costo cargado)
		assertThat(jdbc.queryForObject("select costo_unitario from detalles_venta", BigDecimal.class))
			.isEqualByComparingTo("200");
	}

	@Test
	void comboDeGrupoEligeAutomaticamenteOSegunLoIndicado() {
		UUID bebidas = categoria("Bebidas");
		producto("B1", "Cola", bebidas, "100", "300", "1");
		producto("B2", "Lima", bebidas, "100", "300", "4");
		productos.crearCombo(new ComboRequest("C2", "3 bebidas", null, null, "UN", "Combo",
			BigDecimal.ZERO, new BigDecimal("800"), null, null, true, null, bebidas, 3));
		abrirCaja("0");

		vender("EFECTIVO", "800", null, item("C2", "1"));
		// Toma primero del que mas stock tiene
		assertThat(stockDe("B2")).isEqualByComparingTo("1");
		assertThat(stockDe("B1")).isEqualByComparingTo("1");

		// Quedan 2 unidades en total: el combo de 3 ya no alcanza
		assertThat(stockDe("C2")).isEqualByComparingTo("0");
		assertThatThrownBy(() -> vender("EFECTIVO", "800", null, item("C2", "1")))
			.hasMessageContaining("sin stock");
	}

	@Test
	void comboDeGrupoSinStockSuficienteFalla() {
		UUID bebidas = categoria("Bebidas");
		producto("B1", "Cola", bebidas, "100", "300", "2");
		productos.crearCombo(new ComboRequest("C2", "3 bebidas", null, null, "UN", "Combo",
			BigDecimal.ZERO, new BigDecimal("800"), null, null, true, null, bebidas, 3));
		abrirCaja("0");
		assertThatThrownBy(() -> vender("EFECTIVO", "800", null, item("C2", "1")))
			.hasMessageContaining("sin stock");
	}

	@Test
	void comboDeGrupoConEleccionExplicita() {
		UUID bebidas = categoria("Bebidas");
		producto("B1", "Cola", bebidas, "100", "300", "5");
		producto("B2", "Lima", bebidas, "100", "300", "5");
		productos.crearCombo(new ComboRequest("C2", "3 bebidas", null, null, "UN", "Combo",
			BigDecimal.ZERO, new BigDecimal("800"), null, null, true, null, bebidas, 3));
		abrirCaja("0");
		ventas.checkout(new VentaCheckoutRequest(List.of(new VentaCheckoutItemRequest("C2", BigDecimal.ONE,
			List.of(item("B1", "1"), item("B2", "2")))), null, new VentaCheckoutPagoRequest("EFECTIVO", null)));
		assertThat(stockDe("B1")).isEqualByComparingTo("4");
		assertThat(stockDe("B2")).isEqualByComparingTo("3");

		assertThatThrownBy(() -> ventas.checkout(new VentaCheckoutRequest(List.of(new VentaCheckoutItemRequest("C2", BigDecimal.ONE,
			List.of(item("B1", "1")))), null, new VentaCheckoutPagoRequest("EFECTIVO", null))))
			.hasMessageContaining("lleva 3");
	}

	@Test
	void anularDevuelveStockYSoloMientrasLaCajaEstaAbierta() {
		producto("A1", "Agua", null, "100", "300", "10");
		abrirCaja("0");
		VentaCheckoutResponse venta = vender("EFECTIVO", "900", null, item("A1", "3"));
		ventas.anularVenta(venta.saleId());
		assertThat(stockDe("A1")).isEqualByComparingTo("10");
		assertThat(tesoreria.obtenerResumen().sales()).isEqualByComparingTo("0");

		VentaCheckoutResponse otra = vender("EFECTIVO", "300", null, item("A1", "1"));
		tesoreria.cerrarCaja(new CierreCajaRequest(List.of(new ConteoBilleteRequest(new BigDecimal("100"), 3)), null));
		assertThatThrownBy(() -> ventas.anularVenta(otra.saleId())).hasMessageContaining("caja cerrada");
	}

	@Test
	void cierreDeCajaCalculaDiferencia() {
		producto("A1", "Agua", null, "100", "300", "10");
		abrirCaja("1000");
		vender("EFECTIVO", "300", null, item("A1", "1"));
		tesoreria.registrarMovimientoManual(new MovimientoManualRequest(TipoMovimientoTesoreria.EGRESO, "Hielo", null, new BigDecimal("200")));
		var cierre = tesoreria.cerrarCaja(new CierreCajaRequest(
			List.of(new ConteoBilleteRequest(new BigDecimal("1000"), 1), new ConteoBilleteRequest(new BigDecimal("50"), 1)), "ok"));
		assertThat(cierre.expectedCash()).isEqualByComparingTo("1100");
		assertThat(cierre.countedCash()).isEqualByComparingTo("1050");
		assertThat(cierre.difference()).isEqualByComparingTo("-50");
		assertThat(tesoreria.obtenerEstadoCaja().open()).isFalse();
		assertThat(tesoreria.listarCajasCerradas(null, null, 0, 10).items()).hasSize(1);
	}

	@Test
	void editarProductoNoPisaLasVentasHechasMientrasTanto() {
		ProductoDto agua = producto("A1", "Agua", null, "100", "300", "10");
		abrirCaja("0");
		// El formulario se abrio mostrando 10; mientras tanto se vendieron 3
		vender("EFECTIVO", "900", null, item("A1", "3"));
		// El usuario cambia el precio y deja el stock como estaba en pantalla (10)
		productos.actualizar(agua.id(), new ProductoRequest("A1", "Agua", null, null, null, "UN", "General",
			new BigDecimal("100"), new BigDecimal("350"), new BigDecimal("10"), new BigDecimal("10"), BigDecimal.ONE, true));
		assertThat(stockDe("A1")).isEqualByComparingTo("7");
		// Y si corrige el stock de 10 a 12, se suman 2
		productos.actualizar(agua.id(), new ProductoRequest("A1", "Agua", null, null, null, "UN", "General",
			new BigDecimal("100"), new BigDecimal("350"), new BigDecimal("12"), new BigDecimal("10"), BigDecimal.ONE, true));
		assertThat(stockDe("A1")).isEqualByComparingTo("9");
		productos.ajustarStock(agua.id(), new AjusteStockRequest(new BigDecimal("20"), "Recuento"));
		assertThat(stockDe("A1")).isEqualByComparingTo("20");
		assertThat(productos.movimientos(agua.id())).hasSize(4);
	}

	@Test
	void recepcionDeCompraSumaStock() {
		ProductoDto agua = producto("A1", "Agua", null, "100", "300", "0");
		proveedores.crear(new ProveedorRequest("1155550000", "Distribuidora", true));
		var solicitud = compras.crear(new SolicitudCompraRequest("1155550000",
			List.of(new SolicitudCompraItemRequest(agua.id(), new BigDecimal("12")))));
		UUID itemId = solicitud.items().get(0).id();
		compras.recepcionar(solicitud.id(), new SolicitudCompraRecepcionRequest(
			List.of(new SolicitudCompraRecepcionItemRequest(itemId, new BigDecimal("5")))));
		assertThat(stockDe("A1")).isEqualByComparingTo("5");
		var completa = compras.recepcionar(solicitud.id(), new SolicitudCompraRecepcionRequest(
			List.of(new SolicitudCompraRecepcionItemRequest(itemId, new BigDecimal("12")))));
		assertThat(stockDe("A1")).isEqualByComparingTo("12");
		assertThat(completa.status()).isEqualTo("RECIBIDA");
	}

	@Test
	void estadisticasUsanElCostoDeLaVentaYDescuentan() {
		ProductoDto agua = producto("A1", "Agua", null, "100", "300", "10");
		abrirCaja("0");
		vender("EFECTIVO", null, "100", item("A1", "2"));
		// Subir el costo despues no cambia la ganancia ya registrada
		productos.actualizar(agua.id(), new ProductoRequest("A1", "Agua", null, null, null, "UN", "General",
			new BigDecimal("250"), new BigDecimal("300"), null, null, BigDecimal.ONE, true));
		var kpis = estadisticas.obtener("hoy").kpis();
		assertThat(kpis.revenue()).isEqualByComparingTo("500");
		assertThat(kpis.profit()).isEqualByComparingTo("300");
	}
}
