package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.example.demo.dto.AperturaCajaRequest;
import com.example.demo.dto.CategoriaRequest;
import com.example.demo.dto.ProductoDto;
import com.example.demo.dto.ProductoRequest;
import com.example.demo.dto.SyncResultadoDto;
import com.example.demo.dto.VentaCheckoutItemRequest;
import com.example.demo.dto.VentaCheckoutPagoRequest;
import com.example.demo.dto.VentaCheckoutRequest;
import com.example.demo.servicio.CategoriaService;
import com.example.demo.servicio.ProductoService;
import com.example.demo.servicio.TesoreriaService;
import com.example.demo.servicio.VentaService;
import com.example.demo.sync.MigradorNube;
import com.example.demo.sync.SyncService;

/**
 * Sincronizacion contra una segunda base que hace de Supabase (mismas migraciones +
 * db/cloud). Necesita REYDEREYES_TEST_NUBE_URL apuntando a una base vacia del mismo
 * servidor, con un usuario administrador.
 */
@EnabledIfEnvironmentVariable(named = "REYDEREYES_TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "REYDEREYES_TEST_NUBE_URL", matches = ".+")
class SincronizacionTest extends BaseIntegracionTest {

	private static final String CLAVE_SYNC = "clave-de-prueba";
	private static JdbcTemplate nube;

	@Autowired SyncService sync;
	@Autowired ProductoService productos;
	@Autowired CategoriaService categorias;
	@Autowired VentaService ventas;
	@Autowired TesoreriaService tesoreria;

	@DynamicPropertySource
	static void nube(DynamicPropertyRegistry registry) {
		registry.add("app.sync.habilitada", () -> "true");
		registry.add("app.sync.url", () -> System.getenv("REYDEREYES_TEST_NUBE_URL"));
		registry.add("app.sync.usuario", () -> "sync_local");
		registry.add("app.sync.clave", () -> CLAVE_SYNC);
	}

	@BeforeAll
	static void prepararNube() {
		String url = System.getenv("REYDEREYES_TEST_NUBE_URL");
		String usuario = env("REYDEREYES_TEST_DB_USUARIO", "postgres");
		String clave = env("REYDEREYES_TEST_DB_CLAVE", "");
		Flyway.configure().dataSource(url, usuario, clave)
			.locations("classpath:db/migration", "classpath:db/cloud")
			.cleanDisabled(false).load().clean();
		MigradorNube.migrar(url, usuario, clave);
		nube = new JdbcTemplate(new SingleConnectionDataSource(url, usuario, clave, true));
		nube.execute("alter role sync_local with login password '" + CLAVE_SYNC + "'");
	}

	@BeforeEach
	void limpiarNube() {
		nube.execute("""
			truncate movimientos_tesoreria, movimientos_stock, solicitudes_compra_items, solicitudes_compra,
			         pagos, detalles_venta, ventas, cajas, clientes, productos, proveedores, tipos, categorias,
			         sync_cambios, sync_conflictos, sync_recibidos cascade
			""");
	}

	// ------------------------------------------------------------------ auxiliares

	private ProductoDto producto(String barcode, String precio, String stock) {
		return productos.crear(new ProductoRequest(barcode, "Producto " + barcode, null, null, null, "UN", "General",
			new BigDecimal("10"), new BigDecimal(precio), new BigDecimal(stock), null, BigDecimal.ZERO, true));
	}

	private void venderUno(String barcode) {
		ventas.checkout(new VentaCheckoutRequest(List.of(new VentaCheckoutItemRequest(barcode, BigDecimal.ONE, null)),
			null, new VentaCheckoutPagoRequest("EFECTIVO", null)));
	}

	private long contarNube(String tabla) {
		return nube.queryForObject("select count(*) from " + tabla, Long.class);
	}

	/** Simula una edicion desde la web: rol authenticated, sin modo sincronizacion. */
	private void editarComoDuenioWeb(String sql, Object... args) {
		nube.execute("begin");
		try {
			nube.execute("set local role authenticated");
			nube.update(sql, args);
			nube.execute("commit");
		} catch (RuntimeException e) {
			nube.execute("rollback");
			throw e;
		}
	}

	private BigDecimal precioLocal(String barcode) {
		return jdbc.queryForObject("select precio_venta from productos where codigo_barra = ?", BigDecimal.class, barcode);
	}

	private BigDecimal precioNube(String barcode) {
		return nube.queryForObject("select precio_venta from productos where codigo_barra = ?", BigDecimal.class, barcode);
	}

	// ------------------------------------------------------------------ pruebas

	@Test
	void subeTodoLoLocalYReenviarNoDuplica() {
		categorias.crear(new CategoriaRequest("Bebidas", null));
		producto("A1", "300", "10");
		tesoreria.abrirCaja(new AperturaCajaRequest(new BigDecimal("500"), null));
		venderUno("A1");
		assertThat(sync.pendientes()).isPositive();

		SyncResultadoDto resultado = sync.sincronizar();

		assertThat(resultado.ok()).as(resultado.message()).isTrue();
		assertThat(sync.pendientes()).isZero();
		assertThat(contarNube("productos")).isEqualTo(1);
		assertThat(contarNube("ventas")).isEqualTo(1);
		assertThat(contarNube("detalles_venta")).isEqualTo(1);
		assertThat(contarNube("pagos")).isEqualTo(1);
		assertThat(contarNube("movimientos_stock")).isEqualTo(2);
		assertThat(contarNube("movimientos_tesoreria")).isEqualTo(2);
		assertThat(nube.queryForObject("select cantidad from v_stock", BigDecimal.class)).isEqualByComparingTo("9");
		// Lo que llega del local no vuelve a salir como cambio de la nube
		assertThat(contarNube("sync_cambios")).isZero();

		// Reenviar los mismos cambios (respuesta perdida) no duplica nada
		jdbc.update("update sync_cambios set estado = 'PENDIENTE'");
		assertThat(sync.sincronizar().ok()).isTrue();
		assertThat(contarNube("ventas")).isEqualTo(1);
		assertThat(sync.estado().level()).isEqualTo("VERDE");
	}

	@Test
	void bajaLosCambiosDeCatalogoHechosEnLaWeb() {
		producto("A1", "300", "10");
		sync.sincronizar();

		editarComoDuenioWeb("update productos set precio_venta = 450 where codigo_barra = 'A1'");
		editarComoDuenioWeb("insert into categorias (nombre) values ('Creada en la web')");

		SyncResultadoDto resultado = sync.sincronizar();

		assertThat(resultado.pulled()).isEqualTo(2);
		assertThat(precioLocal("A1")).isEqualByComparingTo("450");
		assertThat(jdbc.queryForObject("select origen from categorias", String.class)).isEqualTo("PROD");
		// Aplicar lo que vino de la nube no genera cambios locales para volver a subir
		assertThat(sync.pendientes()).isZero();
		Long versionLocal = jdbc.queryForObject("select version from productos", Long.class);
		Long versionNube = nube.queryForObject("select version from productos", Long.class);
		assertThat(versionLocal).isEqualTo(versionNube);
	}

	@Test
	void laWebNoPuedeTocarVentasNiCajas() {
		tesoreria.abrirCaja(new AperturaCajaRequest(BigDecimal.ZERO, null));
		sync.sincronizar();
		assertThatThrownBy(() -> editarComoDuenioWeb("update cajas set monto_apertura = 999"))
			.rootCause().hasMessageContaining("permission denied");
		nube.execute("begin");
		nube.execute("set local role authenticated");
		Long cajasVisibles = nube.queryForObject("select count(*) from cajas", Long.class);
		nube.execute("rollback");
		assertThat(cajasVisibles).isEqualTo(1);
	}

	@Test
	void conflictoGanaElUltimoCambio() throws Exception {
		ProductoDto a1 = producto("A1", "300", "10");
		producto("B1", "300", "10");
		sync.sincronizar();

		// A1: primero la web, despues el local -> gana el local
		editarComoDuenioWeb("update productos set precio_venta = 400 where codigo_barra = 'A1'");
		Thread.sleep(20);
		jdbc.update("update productos set precio_venta = 500 where codigo_barra = 'A1'");
		// B1: primero el local, despues la web -> gana la web
		jdbc.update("update productos set precio_venta = 600 where codigo_barra = 'B1'");
		Thread.sleep(20);
		editarComoDuenioWeb("update productos set precio_venta = 700 where codigo_barra = 'B1'");

		SyncResultadoDto resultado = sync.sincronizar();

		assertThat(resultado.conflicts()).isEqualTo(2);
		assertThat(precioNube("A1")).isEqualByComparingTo("500");
		assertThat(precioLocal("A1")).isEqualByComparingTo("500");
		assertThat(precioNube("B1")).isEqualByComparingTo("700");
		assertThat(precioLocal("B1")).isEqualByComparingTo("700");
		assertThat(sync.conflictos()).hasSize(2);
		assertThat(sync.estado().unreviewedConflicts()).isEqualTo(2);

		// Despues del conflicto ambos lados quedan en la misma version y siguen sincronizando
		jdbc.update("update productos set precio_venta = 510 where id = ?", a1.id());
		assertThat(sync.sincronizar().conflicts()).isZero();
		assertThat(precioNube("A1")).isEqualByComparingTo("510");
	}

	@Test
	void mismoCodigoDeBarrasEnAmbosLadosSeFusiona() {
		editarComoDuenioWeb("""
			insert into productos (codigo_barra, nombre, precio_costo, precio_venta, unidad_medida)
			values ('X1', 'Creado en la web', 10, 999, 'UN')""");
		UUID idNube = nube.queryForObject("select id from productos", UUID.class);

		producto("X1", "300", "5");
		tesoreria.abrirCaja(new AperturaCajaRequest(BigDecimal.ZERO, null));
		venderUno("X1");

		SyncResultadoDto resultado = sync.sincronizar();

		assertThat(resultado.ok()).as(resultado.message()).isTrue();
		assertThat(contarNube("productos")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from productos", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select id from productos", UUID.class)).isEqualTo(idNube);
		// La venta y el stock quedaron apuntando al producto de la nube, en ambos lados
		assertThat(nube.queryForObject("select producto_id from detalles_venta", UUID.class)).isEqualTo(idNube);
		assertThat(nube.queryForObject("select cantidad from v_stock", BigDecimal.class)).isEqualByComparingTo("4");
		assertThat(sync.pendientes()).isZero();
	}

	@Test
	void unaPcNuevaSeRestauraDesdeLaNube() {
		categorias.crear(new CategoriaRequest("Bebidas", null));
		producto("A1", "300", "10");
		tesoreria.abrirCaja(new AperturaCajaRequest(new BigDecimal("100"), null));
		venderUno("A1");
		venderUno("A1");
		assertThat(sync.sincronizar().ok()).isTrue();

		// La PC se rompe: base local vacia
		limpiarBaseLocal();
		assertThat(jdbc.queryForObject("select count(*) from productos", Long.class)).isZero();

		SyncResultadoDto resultado = sync.restaurarDesdeNube();

		assertThat(resultado.ok()).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from ventas", Long.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select cantidad from v_stock", BigDecimal.class)).isEqualByComparingTo("8");
		assertThat(tesoreria.obtenerEstadoCaja().open()).isTrue();
		assertThat(sync.pendientes()).isZero();
		// La numeracion de tickets continua
		venderUno("A1");
		assertThat(jdbc.queryForObject("select max(numero) from ventas", Long.class)).isEqualTo(3);
		assertThat(sync.sincronizar().ok()).isTrue();
		assertThat(contarNube("ventas")).isEqualTo(3);

		assertThatThrownBy(() -> sync.restaurarDesdeNube()).hasMessageContaining("ya tiene datos");
	}
}
