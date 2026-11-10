package com.example.demo.controlador;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.dto.ConflictoDto;
import com.example.demo.dto.SyncEstadoDto;
import com.example.demo.dto.SyncResultadoDto;
import com.example.demo.sync.SyncService;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

	private final SyncService syncService;

	public SyncController(SyncService syncService) {
		this.syncService = syncService;
	}

	/** Para el indicador de la barra superior (verde / amarillo / rojo). */
	@GetMapping("/estado")
	public SyncEstadoDto estado() {
		return syncService.estado();
	}

	/** Boton "Sincronizar ahora". */
	@PostMapping("/ahora")
	public SyncResultadoDto sincronizarAhora() {
		return syncService.sincronizar();
	}

	@GetMapping("/conflictos")
	public List<ConflictoDto> conflictos() {
		return syncService.conflictos();
	}

	@PatchMapping("/conflictos/{id}/revisado")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void marcarRevisado(@PathVariable UUID id) {
		syncService.marcarConflictoRevisado(id);
	}

	/** Llena una PC nueva con los datos de la nube. Solo funciona con la base local vacia. */
	@PostMapping("/restaurar")
	public SyncResultadoDto restaurar() {
		try {
			return syncService.restaurarDesdeNube();
		} catch (IllegalStateException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
		}
	}
}
