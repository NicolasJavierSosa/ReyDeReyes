package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base de las pruebas de integracion contra PostgreSQL real.
 *
 * Variables de entorno:
 *   REYDEREYES_TEST_DB_URL      jdbc de una base vacia para el "local"  (obligatoria)
 *   REYDEREYES_TEST_DB_USUARIO  / REYDEREYES_TEST_DB_CLAVE
 *   REYDEREYES_TEST_NUBE_URL    jdbc de otra base que simula Supabase  (pruebas de sincronizacion)
 */
@SpringBootTest(properties = {
	"app.sync.demora-inicial=PT10H",
	"server.address=127.0.0.1"
})
@EnabledIfEnvironmentVariable(named = "REYDEREYES_TEST_DB_URL", matches = ".+")
public abstract class BaseIntegracionTest {

	@Autowired
	protected JdbcTemplate jdbc;

	@DynamicPropertySource
	static void propiedades(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getenv("REYDEREYES_TEST_DB_URL"));
		registry.add("spring.datasource.username", () -> env("REYDEREYES_TEST_DB_USUARIO", "postgres"));
		registry.add("spring.datasource.password", () -> env("REYDEREYES_TEST_DB_CLAVE", ""));
	}

	protected static String env(String nombre, String porDefecto) {
		String valor = System.getenv(nombre);
		return valor == null || valor.isBlank() ? porDefecto : valor;
	}

	/** Deja la base local vacia (conserva unidades y metodos de pago iniciales). */
	@BeforeEach
	void limpiarBaseLocal() {
		jdbc.execute("""
			truncate movimientos_tesoreria, movimientos_stock, solicitudes_compra_items, solicitudes_compra,
			         pagos, detalles_venta, ventas, cajas, clientes, productos, proveedores, tipos, categorias,
			         sync_cambios, sync_conflictos cascade
			""");
		jdbc.execute("delete from configuracion where clave like 'sync.%'");
		jdbc.execute("alter sequence ventas_numero_seq restart with 1");
		jdbc.execute("delete from unidades_medida where abreviatura not in ('UN','KG','G','L','ML')");
		jdbc.execute("delete from metodos_pago where codigo not in ('EFECTIVO','TRANSFERENCIA','TARJETA','QR')");
	}
}
