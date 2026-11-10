package com.example.demo.servicio;

import static com.example.demo.util.Valores.conflicto;
import static com.example.demo.util.Valores.noEncontrado;
import static com.example.demo.util.Valores.requerido;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.MetodoPagoDto;
import com.example.demo.dto.MetodoPagoRequest;
import com.example.demo.modelo.MetodoPago;
import com.example.demo.repositorio.MetodoPagoRepository;

/** Lista de metodos de pago que mantiene el duenio. Son solo un registro: no integran ningun medio. */
@Service
public class MetodoPagoService {

	private final MetodoPagoRepository metodoPagoRepository;

	public MetodoPagoService(MetodoPagoRepository metodoPagoRepository) {
		this.metodoPagoRepository = metodoPagoRepository;
	}

	@Transactional(readOnly = true)
	public List<MetodoPagoDto> listar(boolean soloActivos) {
		List<MetodoPago> metodos = soloActivos
			? metodoPagoRepository.findByActivoTrueOrderByOrdenAscNombreAsc()
			: metodoPagoRepository.findAllByOrderByOrdenAscNombreAsc();
		return metodos.stream().map(this::toDto).toList();
	}

	@Transactional
	public MetodoPagoDto crear(MetodoPagoRequest request) {
		String codigo = codigo(request.code());
		if (metodoPagoRepository.existsByCodigoIgnoreCase(codigo)) {
			throw conflicto("Ya existe un metodo con ese codigo");
		}
		MetodoPago metodo = new MetodoPago();
		metodo.setCodigo(codigo);
		apply(metodo, request);
		return toDto(metodoPagoRepository.save(metodo));
	}

	@Transactional
	public MetodoPagoDto actualizar(UUID id, MetodoPagoRequest request) {
		MetodoPago metodo = metodoPagoRepository.findById(id)
			.orElseThrow(() -> noEncontrado("Metodo de pago no encontrado"));
		// El codigo no cambia: las ventas ya registradas lo referencian
		apply(metodo, request);
		return toDto(metodoPagoRepository.save(metodo));
	}

	private void apply(MetodoPago metodo, MetodoPagoRequest request) {
		metodo.setNombre(requerido(request.name(), "Nombre requerido"));
		metodo.setAfectaCaja(Boolean.TRUE.equals(request.affectsCash()));
		metodo.setActivo(request.active() == null || request.active());
		metodo.setOrden(request.order() == null ? 99 : request.order());
	}

	private String codigo(String raw) {
		return requerido(raw, "Codigo requerido").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
	}

	private MetodoPagoDto toDto(MetodoPago metodo) {
		return new MetodoPagoDto(
			metodo.getId(),
			metodo.getCodigo(),
			metodo.getNombre(),
			Boolean.TRUE.equals(metodo.getAfectaCaja()),
			Boolean.TRUE.equals(metodo.getActivo()),
			metodo.getOrden() == null ? 0 : metodo.getOrden()
		);
	}
}
