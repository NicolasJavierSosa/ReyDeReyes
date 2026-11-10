package com.example.demo.dto;

public record SyncResultadoDto(
	boolean ok,
	int pushed,
	int pulled,
	int conflicts,
	String message
) {
	public static SyncResultadoDto desactivada() {
		return new SyncResultadoDto(false, 0, 0, 0, "La sincronizacion con la nube no esta configurada");
	}
}
