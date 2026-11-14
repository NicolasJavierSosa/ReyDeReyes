package com.example.demo.controlador;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Devuelve siempre el motivo del error en {"message": "..."} para que la pantalla lo muestre
 * (por ejemplo "No hay una caja abierta" o "Stock insuficiente para ..."), sin depender de la
 * configuracion de errores por defecto de Spring.
 */
@RestControllerAdvice
public class ManejadorErrores {

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> estado(ResponseStatusException e) {
		String motivo = e.getReason() != null ? e.getReason() : HttpStatus.valueOf(e.getStatusCode().value()).getReasonPhrase();
		return respuesta(e.getStatusCode(), motivo);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
		String detalle = e.getBindingResult().getFieldErrors().stream()
			.map(f -> f.getField() + " " + f.getDefaultMessage())
			.collect(Collectors.joining("; "));
		return respuesta(HttpStatus.BAD_REQUEST, "Datos invalidos: " + detalle);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> ilegible(HttpMessageNotReadableException e) {
		return respuesta(HttpStatus.BAD_REQUEST, "Los datos enviados no se pudieron leer");
	}

	private ResponseEntity<Map<String, Object>> respuesta(HttpStatusCode codigo, String mensaje) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("status", codigo.value());
		cuerpo.put("message", mensaje);
		return ResponseEntity.status(codigo).body(cuerpo);
	}
}
