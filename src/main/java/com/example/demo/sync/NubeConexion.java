package com.example.demo.sync;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Conexion a la base de Supabase, separada de la base local. Se crea recien cuando
 * se usa, para que el sistema arranque y venda aunque no haya internet.
 */
@Component
public class NubeConexion implements DisposableBean {

	private final SyncProperties properties;
	private volatile HikariDataSource dataSource;
	private volatile JdbcTemplate jdbc;

	public NubeConexion(SyncProperties properties) {
		this.properties = properties;
	}

	public boolean configurada() {
		return properties.configurada();
	}

	public JdbcTemplate jdbc() {
		if (jdbc == null) {
			synchronized (this) {
				if (jdbc == null) {
					HikariConfig config = new HikariConfig();
					config.setJdbcUrl(properties.url());
					config.setUsername(properties.usuario());
					config.setPassword(properties.clave());
					config.setMaximumPoolSize(2);
					config.setMinimumIdle(0);
					config.setConnectionTimeout(15_000);
					config.setInitializationFailTimeout(-1);
					config.setPoolName("supabase");
					dataSource = new HikariDataSource(config);
					JdbcTemplate template = new JdbcTemplate(dataSource);
					template.setQueryTimeout(60);
					jdbc = template;
				}
			}
		}
		return jdbc;
	}

	@Override
	public void destroy() {
		if (dataSource != null) {
			dataSource.close();
		}
	}
}
