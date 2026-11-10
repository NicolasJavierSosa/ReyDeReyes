package com.example.demo.sync;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Aplica las migraciones a Supabase (las comunes + las propias de la nube).
 * Uso: java -jar demo.jar --migrar-nube
 * Necesita SUPABASE_ADMIN_URL, SUPABASE_ADMIN_USUARIO y SUPABASE_ADMIN_CLAVE
 * (el usuario postgres del proyecto, no el rol sync_local).
 */
public final class MigradorNube {

	private MigradorNube() {
	}

	public static void ejecutar() {
		MigrateResult result = migrar(
			requerida("SUPABASE_ADMIN_URL"),
			requerida("SUPABASE_ADMIN_USUARIO"),
			requerida("SUPABASE_ADMIN_CLAVE"));
		System.out.println("Migraciones aplicadas en la nube: " + result.migrationsExecuted
			+ " (version actual " + result.targetSchemaVersion + ")");
	}

	public static MigrateResult migrar(String url, String usuario, String clave) {
		MigrateResult result = Flyway.configure()
			.dataSource(url, usuario, clave)
			.locations("classpath:db/migration", "classpath:db/cloud")
			.load()
			.migrate();
		// El historial de Flyway queda en el esquema public: que la web no lo pueda leer
		try (Connection connection = DriverManager.getConnection(url, usuario, clave);
			 Statement statement = connection.createStatement()) {
			statement.execute("alter table flyway_schema_history enable row level security");
			statement.execute("revoke all on table flyway_schema_history from anon, authenticated");
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo proteger flyway_schema_history: " + e.getMessage(), e);
		}
		return result;
	}

	private static String requerida(String nombre) {
		String valor = System.getenv(nombre);
		if (valor == null || valor.isBlank()) {
			throw new IllegalStateException("Falta la variable de entorno " + nombre);
		}
		return valor;
	}
}
