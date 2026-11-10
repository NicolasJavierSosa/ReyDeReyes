package com.example.demo.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Normalizacion de textos y redondeo de montos y cantidades. */
public final class Valores {

	private Valores() {
	}

	public static String texto(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}

	public static String textoONull(String value) {
		String normalized = texto(value);
		return normalized.isEmpty() ? null : normalized;
	}

	public static String requerido(String value, String mensaje) {
		String normalized = texto(value);
		if (normalized.isEmpty()) {
			throw error(mensaje);
		}
		return normalized;
	}

	public static BigDecimal cero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	public static BigDecimal dinero(BigDecimal value) {
		return cero(value).setScale(2, RoundingMode.HALF_UP);
	}

	public static BigDecimal cantidad(BigDecimal value) {
		return cero(value).setScale(3, RoundingMode.HALF_UP);
	}

	public static ResponseStatusException error(String mensaje) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
	}

	public static ResponseStatusException noEncontrado(String mensaje) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, mensaje);
	}

	public static ResponseStatusException conflicto(String mensaje) {
		return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
	}

	public static String ticket(Long numero) {
		return String.format("%06d", numero == null ? 0 : numero);
	}
}
