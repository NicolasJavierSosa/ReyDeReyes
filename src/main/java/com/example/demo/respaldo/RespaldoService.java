package com.example.demo.respaldo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Copias con pg_dump:
 *  - diaria de la base local, al cerrar la caja (a un segundo disco o pendrive);
 *  - semanal de Supabase, porque el plan gratis no tiene backups propios.
 * Cada una se activa solo si su carpeta / conexion esta configurada.
 */
@Service
public class RespaldoService {

	private static final Logger log = LoggerFactory.getLogger(RespaldoService.class);
	private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

	private final String pgDump;
	private final String carpeta;
	private final String localUrl;
	private final String localUsuario;
	private final String localClave;
	private final String nubeUrl;
	private final String nubeClave;
	private final int conservar;

	public RespaldoService(
		@Value("${app.respaldo.pg-dump:pg_dump}") String pgDump,
		@Value("${app.respaldo.carpeta:}") String carpeta,
		@Value("${app.respaldo.local-url:}") String localUrl,
		@Value("${spring.datasource.username:}") String localUsuario,
		@Value("${spring.datasource.password:}") String localClave,
		@Value("${app.respaldo.nube-url:}") String nubeUrl,
		@Value("${app.respaldo.nube-clave:}") String nubeClave,
		@Value("${app.respaldo.conservar:30}") int conservar
	) {
		this.pgDump = pgDump;
		this.carpeta = carpeta;
		this.localUrl = localUrl;
		this.localUsuario = localUsuario;
		this.localClave = localClave;
		this.nubeUrl = nubeUrl;
		this.nubeClave = nubeClave;
		this.conservar = conservar;
	}

	/** Copia de la base local. Devuelve un mensaje para mostrar o null si no esta configurada. */
	public String copiaLocal() {
		if (carpeta.isBlank() || localUrl.isBlank()) {
			return null;
		}
		return copiar("local", localUrl, localUsuario, localClave);
	}

	@Scheduled(cron = "${app.respaldo.cron-nube:0 30 22 * * SUN}")
	public void copiaSemanalNube() {
		if (carpeta.isBlank() || nubeUrl.isBlank()) {
			return;
		}
		copiar("nube", nubeUrl, null, nubeClave);
	}

	private String copiar(String prefijo, String url, String usuario, String clave) {
		try {
			Path destino = Path.of(carpeta);
			Files.createDirectories(destino);
			Path archivo = destino.resolve("reydereyes-" + prefijo + "-" + LocalDateTime.now().format(FORMATO) + ".dump");
			List<String> comando = new ArrayList<>(List.of(pgDump, "--format=custom", "--no-owner", "--file=" + archivo, "--dbname=" + url));
			if (usuario != null && !usuario.isBlank()) {
				comando.add("--username=" + usuario);
			}
			ProcessBuilder pb = new ProcessBuilder(comando).redirectErrorStream(true);
			if (clave != null && !clave.isBlank()) {
				pb.environment().put("PGPASSWORD", clave);
			}
			Process proceso = pb.start();
			String salida = new String(proceso.getInputStream().readAllBytes());
			if (!proceso.waitFor(10, TimeUnit.MINUTES) || proceso.exitValue() != 0) {
				log.warn("Fallo la copia {}: {}", prefijo, salida);
				return "No se pudo hacer la copia " + prefijo;
			}
			limpiarViejas(destino, prefijo);
			log.info("Copia {} guardada en {}", prefijo, archivo);
			return "Copia guardada en " + archivo;
		} catch (Exception e) {
			log.warn("Fallo la copia {}: {}", prefijo, e.getMessage());
			return "No se pudo hacer la copia " + prefijo;
		}
	}

	private void limpiarViejas(Path destino, String prefijo) {
		File[] archivos = destino.toFile().listFiles((dir, name) -> name.startsWith("reydereyes-" + prefijo + "-"));
		if (archivos == null || archivos.length <= conservar) {
			return;
		}
		java.util.Arrays.sort(archivos, java.util.Comparator.comparing(File::getName));
		for (int i = 0; i < archivos.length - conservar; i++) {
			archivos[i].delete();
		}
	}
}
