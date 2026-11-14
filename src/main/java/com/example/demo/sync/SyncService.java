package com.example.demo.sync;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.demo.dto.ConflictoDto;
import com.example.demo.dto.SyncEstadoDto;
import com.example.demo.dto.SyncResultadoDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Sincronizacion local <-> Supabase.
 *
 * Push: los triggers anotan cada cambio local en sync_cambios; aca se leen las filas
 * completas, se envian a sync_push() en la nube (tabla por tabla, respetando claves
 * foraneas) y se procesa la respuesta de cada una.
 *
 * Pull: se piden a sync_pull() los cambios de catalogo hechos en la web despues del
 * ultimo cursor y se aplican en la base local.
 *
 * Mientras se escriben datos que llegan de la nube se define app.sync_aplicando = 'on'
 * para que los triggers no los vuelvan a anotar como cambios locales.
 */
@Service
public class SyncService {

	private static final Logger log = LoggerFactory.getLogger(SyncService.class);

	static final String CLAVE_CURSOR = "sync.cursor";
	static final String CLAVE_ULTIMO_EXITO = "sync.ultimo_exito";
	static final String CLAVE_ULTIMO_INTENTO = "sync.ultimo_intento";
	static final String CLAVE_ULTIMO_ERROR = "sync.ultimo_error";

	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final NubeConexion nube;
	private final SyncProperties properties;
	private final ObjectMapper mapper = new ObjectMapper();

	public SyncService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, NubeConexion nube, SyncProperties properties) {
		this.jdbc = jdbc;
		this.tx = new TransactionTemplate(transactionManager);
		this.nube = nube;
		this.properties = properties;
	}

	// ================================================================== API publica

	/** Sube los cambios pendientes y baja los del catalogo. Nunca corre dos veces a la vez. */
	public synchronized SyncResultadoDto sincronizar() {
		if (!nube.configurada()) {
			return SyncResultadoDto.desactivada();
		}
		guardar(CLAVE_ULTIMO_INTENTO, OffsetDateTime.now().toString());
		try {
			Contadores contadores = new Contadores();
			push(contadores);
			pull(contadores);
			purgarEnviados();
			guardar(CLAVE_ULTIMO_EXITO, OffsetDateTime.now().toString());
			guardar(CLAVE_ULTIMO_ERROR, null);
			String mensaje = contadores.errores > 0
				? contadores.errores + " cambios no se pudieron subir; se reintentara"
				: "Sincronizacion completa";
			return new SyncResultadoDto(contadores.errores == 0, contadores.subidos, contadores.bajados, contadores.conflictos, mensaje);
		} catch (Exception e) {
			String mensaje = mensajeDe(e);
			log.warn("No se pudo sincronizar: {}", mensaje);
			guardar(CLAVE_ULTIMO_ERROR, mensaje);
			return new SyncResultadoDto(false, 0, 0, 0, "Sin conexion con la nube: " + mensaje);
		}
	}

	public SyncEstadoDto estado() {
		long pendientes = pendientes();
		long conflictos = valorLong("select count(*) from sync_conflictos where not revisado");
		OffsetDateTime ultimoExito = fecha(leer(CLAVE_ULTIMO_EXITO));
		OffsetDateTime ultimoIntento = fecha(leer(CLAVE_ULTIMO_INTENTO));
		String nivel;
		if (!nube.configurada()) {
			nivel = "DESACTIVADA";
		} else if (pendientes == 0 && leer(CLAVE_ULTIMO_ERROR) == null) {
			nivel = "VERDE";
		} else {
			OffsetDateTime primerPendiente = jdbc.queryForObject(
				"select min(creado_en) from sync_cambios where estado = 'PENDIENTE'", OffsetDateTime.class);
			OffsetDateTime referencia = primerPendiente != null ? primerPendiente : ultimoExito;
			boolean viejo = referencia == null
				|| referencia.isBefore(OffsetDateTime.now().minus(properties.alertaSinSubir()));
			nivel = viejo ? "ROJO" : "AMARILLO";
		}
		return new SyncEstadoDto(nivel, nube.configurada(), pendientes, conflictos, ultimoExito, ultimoIntento, leer(CLAVE_ULTIMO_ERROR));
	}

	public long pendientes() {
		return valorLong("select count(*) from sync_cambios where estado = 'PENDIENTE'");
	}

	public List<ConflictoDto> conflictos() {
		return jdbc.query(
			"select id, tabla, entidad_id, ganador, datos_ganador::text, datos_perdedor::text, detectado_en, revisado "
				+ "from sync_conflictos order by detectado_en desc limit 200",
			(rs, i) -> new ConflictoDto(
				rs.getObject("id", UUID.class),
				rs.getString("tabla"),
				rs.getString("entidad_id"),
				rs.getString("ganador"),
				rs.getString("datos_ganador"),
				rs.getString("datos_perdedor"),
				rs.getObject("detectado_en", OffsetDateTime.class),
				rs.getBoolean("revisado")
			));
	}

	public void marcarConflictoRevisado(UUID id) {
		jdbc.update("update sync_conflictos set revisado = true where id = ?", id);
	}

	/**
	 * Llena una PC nueva (base vacia) con todo lo que hay en la nube. Se usa si la
	 * computadora del local se rompe.
	 */
	public synchronized SyncResultadoDto restaurarDesdeNube() {
		if (!nube.configurada()) {
			return SyncResultadoDto.desactivada();
		}
		long datos = valorLong("select (select count(*) from productos) + (select count(*) from ventas) + (select count(*) from cajas)");
		if (datos > 0) {
			throw new IllegalStateException("La base local ya tiene datos; la restauracion solo se hace en una PC nueva");
		}
		JdbcTemplate remoto = nube.jdbc();
		Long cursor = remoto.queryForObject("select sync_cursor_actual()", Long.class);
		int total = 0;
		for (Map<String, Object> t : jdbc.queryForList("select tabla, pk from sync_tablas order by orden")) {
			String tabla = (String) t.get("tabla");
			String pk = (String) t.get("pk");
			String desde = "";
			while (true) {
				String json = remoto.queryForObject("select sync_exportar(?, ?, ?)::text", String.class, tabla, desde, 500);
				JsonNode filas = leerJson(json);
				if (filas.isEmpty()) {
					break;
				}
				tx.executeWithoutResult(status -> {
					modoAplicando();
					for (JsonNode fila : filas) {
						aplicarFila(tabla, fila);
					}
				});
				total += filas.size();
				desde = filas.get(filas.size() - 1).get(pk).asText();
				if (filas.size() < 500) {
					break;
				}
			}
		}
		jdbc.queryForList("select setval('ventas_numero_seq', greatest(coalesce((select max(numero) from ventas where numero < 900000), 0), 1), (select count(*) > 0 from ventas where numero < 900000))");
		jdbc.update("delete from sync_cambios");
		guardar(CLAVE_CURSOR, String.valueOf(cursor == null ? 0 : cursor));
		guardar(CLAVE_ULTIMO_EXITO, OffsetDateTime.now().toString());
		log.info("Restauracion desde la nube completa: {} filas", total);
		return new SyncResultadoDto(true, 0, total, 0, "Restauracion completa: " + total + " registros");
	}

	// ================================================================== push

	private void push(Contadores contadores) {
		JdbcTemplate remoto = nube.jdbc();
		List<String> tablas = jdbc.queryForList("select tabla from sync_tablas order by orden", String.class);
		for (String tabla : tablas) {
			while (true) {
				List<Map<String, Object>> pendientes = jdbc.queryForList(
					"select id, entidad_id, version_base from sync_cambios "
						+ "where estado = 'PENDIENTE' and tabla = ? order by seq limit ?",
					tabla, properties.lote());
				if (pendientes.isEmpty()) {
					break;
				}
				ArrayNode lote = mapper.createArrayNode();
				List<Enviado> enviados = new ArrayList<>();
				for (Map<String, Object> p : pendientes) {
					UUID cambioId = (UUID) p.get("id");
					String entidadId = (String) p.get("entidad_id");
					JsonNode fila = leerFilaLocal(tabla, entidadId);
					if (fila == null) {
						jdbc.update("update sync_cambios set estado = 'ENVIADO', enviado_en = now() where id = ?", cambioId);
						continue;
					}
					ObjectNode cambio = lote.addObject();
					cambio.put("cambioId", cambioId.toString());
					cambio.put("tabla", tabla);
					cambio.put("id", entidadId);
					Object base = p.get("version_base");
					if (base == null) {
						cambio.putNull("versionBase");
					} else {
						cambio.put("versionBase", ((Number) base).longValue());
					}
					cambio.set("fila", fila);
					enviados.add(new Enviado(cambioId, tabla, entidadId, fila.path("version").asLong()));
				}
				if (enviados.isEmpty()) {
					continue;
				}
				String respuesta = remoto.queryForObject("select sync_push(?::jsonb)::text", String.class, lote.toString());
				JsonNode resultados = leerJson(respuesta);
				int errores = 0;
				for (int i = 0; i < resultados.size(); i++) {
					JsonNode resultado = resultados.get(i);
					Enviado enviado = enviados.get(i);
					errores += procesarResultado(enviado, resultado, contadores) ? 0 : 1;
				}
				contadores.subidos += enviados.size() - errores;
				contadores.errores += errores;
				// Si hubo errores se deja para el proximo ciclo (evita reintentar en bucle)
				if (errores > 0 || pendientes.size() < properties.lote()) {
					break;
				}
			}
		}
	}

	/** Devuelve false si el cambio quedo pendiente por un error. */
	private boolean procesarResultado(Enviado enviado, JsonNode resultado, Contadores contadores) {
		String estado = resultado.path("estado").asText();
		switch (estado) {
			case "APLICADO", "DUPLICADO" -> tx.executeWithoutResult(s -> marcarEnviado(enviado, enviado.version()));
			case "LOCAL_GANA" -> {
				long nuevaVersion = resultado.path("version").asLong();
				tx.executeWithoutResult(s -> {
					modoAplicando();
					// La nube guardo la fila local con una version nueva: el local la adopta
					JsonNode actual = leerFilaLocal(enviado.tabla(), enviado.entidadId());
					if (actual != null && actual.path("version").asLong() == enviado.version()) {
						actualizarVersion(enviado, nuevaVersion);
					}
					registrarConflicto(enviado, "LOCAL", null, resultado.path("perdedor"));
					marcarEnviado(enviado, nuevaVersion);
				});
				contadores.conflictos++;
			}
			case "REMOTO_GANA" -> {
				tx.executeWithoutResult(s -> {
					modoAplicando();
					JsonNode local = leerFilaLocal(enviado.tabla(), enviado.entidadId());
					aplicarFila(enviado.tabla(), resultado.path("fila"));
					registrarConflicto(enviado, "NUBE", resultado.path("fila"), local);
					jdbc.update("update sync_cambios set estado = 'ENVIADO', enviado_en = now() where id = ?", enviado.cambioId());
				});
				contadores.conflictos++;
			}
			case "FUSION" -> {
				tx.executeWithoutResult(s -> fusionar(enviado, resultado.path("fila")));
				contadores.conflictos++;
			}
			default -> {
				String error = resultado.path("error").asText("Respuesta desconocida: " + estado);
				log.warn("No se pudo subir {} {}: {}", enviado.tabla(), enviado.entidadId(), error);
				guardar(CLAVE_ULTIMO_ERROR, enviado.tabla() + ": " + error);
				return false;
			}
		}
		return true;
	}

	private void marcarEnviado(Enviado enviado, long versionAceptada) {
		jdbc.update("update sync_cambios set estado = 'ENVIADO', enviado_en = now() where id = ?", enviado.cambioId());
		// Si la fila cambio mientras se subia, queda un nuevo cambio pendiente.
		JsonNode actual = leerFilaLocal(enviado.tabla(), enviado.entidadId());
		if (actual != null && actual.path("version").asLong() != enviado.version()
			&& actual.path("version").asLong() != versionAceptada) {
			jdbc.update(
				"insert into sync_cambios (tabla, entidad_id, version_base) "
					+ "select ?, ?, ? where not exists (select 1 from sync_cambios where tabla = ? and entidad_id = ? and estado = 'PENDIENTE')",
				enviado.tabla(), enviado.entidadId(), versionAceptada, enviado.tabla(), enviado.entidadId());
		}
	}

	private void actualizarVersion(Enviado enviado, long version) {
		String pk = jdbc.queryForObject("select pk from sync_tablas where tabla = ?", String.class, enviado.tabla());
		jdbc.update("update " + identificador(enviado.tabla()) + " set version = ? where " + identificador(pk) + "::text = ?",
			version, enviado.entidadId());
	}

	/**
	 * El local creo un producto (o categoria) que en la nube ya existia con el mismo codigo
	 * de barras (o nombre). Se queda el de la nube y todo lo que apuntaba al local pasa a
	 * apuntar al de la nube.
	 */
	private void fusionar(Enviado enviado, JsonNode remoto) {
		String localId = enviado.entidadId();
		String remotoId = remoto.path("id").asText();
		JsonNode local = leerFilaLocal(enviado.tabla(), localId);

		// 1. Traer la fila de la nube (sin anotarla como cambio local)
		modoAplicando();
		if ("productos".equals(enviado.tabla())) {
			jdbc.update("update productos set codigo_barra = left(codigo_barra, 50) || '#fusion' where id = ?::uuid", localId);
		} else {
			jdbc.update("update categorias set nombre = left(nombre, 100) || ' #fusion' where id = ?::uuid", localId);
		}
		aplicarFila(enviado.tabla(), remoto);

		// 2. Reapuntar referencias (estos cambios si se suben)
		jdbc.execute("set local app.sync_aplicando = 'off'");
		if ("productos".equals(enviado.tabla())) {
			jdbc.update("update detalles_venta set producto_id = ?::uuid where producto_id = ?::uuid", remotoId, localId);
			jdbc.update("update movimientos_stock set producto_id = ?::uuid where producto_id = ?::uuid", remotoId, localId);
			jdbc.update("update solicitudes_compra_items set producto_id = ?::uuid where producto_id = ?::uuid", remotoId, localId);
			jdbc.update(
				"update productos set combo_items = ("
					+ " select jsonb_agg(case when e->>'productoId' = ? then jsonb_set(e, '{productoId}', to_jsonb(?::text)) else e end)"
					+ " from jsonb_array_elements(combo_items) e)"
					+ " where combo_items @> jsonb_build_array(jsonb_build_object('productoId', ?::text))",
				localId, remotoId, localId);
		} else {
			jdbc.update("update productos set categoria_id = ?::uuid where categoria_id = ?::uuid", remotoId, localId);
			jdbc.update("update productos set combo_grupo_categoria_id = ?::uuid where combo_grupo_categoria_id = ?::uuid", remotoId, localId);
		}

		// 3. Borrar la fila local duplicada (nunca llego a la nube)
		jdbc.update("delete from sync_cambios where tabla = ? and entidad_id = ?", enviado.tabla(), localId);
		jdbc.update("delete from " + identificador(enviado.tabla()) + " where id = ?::uuid", localId);
		jdbc.update(
			"insert into sync_conflictos (tabla, entidad_id, ganador, datos_ganador, datos_perdedor) values (?, ?, 'FUSION', ?::jsonb, ?::jsonb)",
			enviado.tabla(), remotoId, remoto.toString(), local == null ? null : local.toString());
	}

	// ================================================================== pull

	private void pull(Contadores contadores) {
		JdbcTemplate remoto = nube.jdbc();
		long cursor = Long.parseLong(java.util.Objects.requireNonNullElse(leer(CLAVE_CURSOR), "0"));
		while (true) {
			String json = remoto.queryForObject("select sync_pull(?, ?)::text", String.class, cursor, properties.lote());
			JsonNode respuesta = leerJson(json);
			JsonNode cambios = respuesta.path("cambios");
			long nuevoCursor = respuesta.path("cursor").asLong(cursor);
			Long fallo = null;
			for (JsonNode cambio : cambios) {
				String tabla = cambio.path("tabla").asText();
				String id = cambio.path("id").asText();
				JsonNode fila = cambio.path("fila");
				Integer pendiente = jdbc.queryForObject(
					"select count(*) from sync_cambios where tabla = ? and entidad_id = ? and estado = 'PENDIENTE'",
					Integer.class, tabla, id);
				if ((pendiente != null && pendiente > 0) || fila.isNull() || fila.isMissingNode()) {
					// Hay un cambio local sin subir: el proximo push resuelve el conflicto
					continue;
				}
				try {
					tx.executeWithoutResult(s -> {
						modoAplicando();
						aplicarFila(tabla, fila);
					});
					contadores.bajados++;
				} catch (Exception e) {
					long seq = cambio.path("seq").asLong();
					log.warn("No se pudo aplicar {} {} de la nube: {}", tabla, id, mensajeDe(e));
					fallo = fallo == null ? seq : Math.min(fallo, seq);
				}
			}
			if (fallo != null) {
				// Se reintenta desde el que fallo en el proximo ciclo
				guardar(CLAVE_CURSOR, String.valueOf(Math.max(cursor, fallo - 1)));
				break;
			}
			guardar(CLAVE_CURSOR, String.valueOf(nuevoCursor));
			if (cambios.size() < properties.lote() || nuevoCursor == cursor) {
				break;
			}
			cursor = nuevoCursor;
		}
	}

	// ================================================================== auxiliares

	private void modoAplicando() {
		jdbc.execute("set local app.sync_aplicando = 'on'");
	}

	private void aplicarFila(String tabla, JsonNode fila) {
		jdbc.queryForList("select sync_aplicar_fila(?, ?::jsonb)", tabla, fila.toString());
	}

	private JsonNode leerFilaLocal(String tabla, String id) {
		String json = jdbc.queryForObject("select sync_leer_fila(?, ?)::text", String.class, tabla, id);
		return json == null ? null : leerJson(json);
	}

	private void registrarConflicto(Enviado enviado, String ganador, JsonNode datosGanador, JsonNode datosPerdedor) {
		jdbc.update(
			"insert into sync_conflictos (tabla, entidad_id, ganador, datos_ganador, datos_perdedor) values (?, ?, ?, ?::jsonb, ?::jsonb)",
			enviado.tabla(), enviado.entidadId(), ganador,
			datosGanador == null || datosGanador.isMissingNode() ? null : datosGanador.toString(),
			datosPerdedor == null || datosPerdedor.isMissingNode() ? null : datosPerdedor.toString());
	}

	private void purgarEnviados() {
		jdbc.update("delete from sync_cambios where estado = 'ENVIADO' and enviado_en < now() - interval '30 days'");
	}

	private JsonNode leerJson(String json) {
		try {
			return mapper.readTree(json == null ? "null" : json);
		} catch (Exception e) {
			throw new IllegalStateException("Respuesta invalida de la nube", e);
		}
	}

	private String leer(String clave) {
		List<String> valores = jdbc.queryForList("select valor from configuracion where clave = ?", String.class, clave);
		return valores.isEmpty() ? null : valores.get(0);
	}

	private void guardar(String clave, String valor) {
		jdbc.update(
			"insert into configuracion (clave, valor) values (?, ?) on conflict (clave) do update set valor = excluded.valor",
			clave, valor);
	}

	private long valorLong(String sql) {
		Long valor = jdbc.queryForObject(sql, Long.class);
		return valor == null ? 0 : valor;
	}

	private static OffsetDateTime fecha(String valor) {
		try {
			return valor == null ? null : OffsetDateTime.parse(valor);
		} catch (Exception e) {
			return null;
		}
	}

	private static String identificador(String nombre) {
		if (!nombre.matches("[a-z_]+")) {
			throw new IllegalArgumentException("Identificador invalido: " + nombre);
		}
		return nombre;
	}

	private static String mensajeDe(Throwable e) {
		Throwable raiz = e;
		while (raiz.getCause() != null && raiz.getCause() != raiz) {
			raiz = raiz.getCause();
		}
		String mensaje = raiz.getMessage();
		return mensaje == null ? raiz.getClass().getSimpleName() : mensaje;
	}

	Duration intervalo() {
		return properties.intervalo();
	}

	private record Enviado(UUID cambioId, String tabla, String entidadId, long version) {}

	private static final class Contadores {
		int subidos;
		int bajados;
		int conflictos;
		int errores;
	}
}
