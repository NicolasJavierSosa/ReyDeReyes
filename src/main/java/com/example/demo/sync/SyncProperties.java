package com.example.demo.sync;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexion a Supabase para sincronizar. Se configura con variables de entorno:
 * SUPABASE_DB_URL, SUPABASE_SYNC_USUARIO, SUPABASE_SYNC_CLAVE.
 */
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(
	boolean habilitada,
	String url,
	String usuario,
	String clave,
	Duration intervalo,
	int lote,
	Duration alertaSinSubir
) {
	public SyncProperties {
		if (intervalo == null) {
			intervalo = Duration.ofMinutes(5);
		}
		if (lote <= 0) {
			lote = 200;
		}
		if (alertaSinSubir == null) {
			alertaSinSubir = Duration.ofHours(1);
		}
	}

	public boolean configurada() {
		return habilitada && url != null && !url.isBlank() && usuario != null && !usuario.isBlank();
	}
}
