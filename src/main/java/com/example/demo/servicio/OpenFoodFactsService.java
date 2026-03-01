package com.example.demo.servicio;

import com.example.demo.dto.OpenFoodFactsProductDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenFoodFactsService {

	private static final String BASE_URL = "https://world.openfoodfacts.org";
	private static final String FIELDS =
		"product_name,product_name_es,generic_name,brands,quantity,categories,image_url";
	private static final String USER_AGENT = "ReyDeReyesPOS/1.0 (contacto@local)";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public OpenFoodFactsService() {
		this.objectMapper = new ObjectMapper();
		this.restClient = RestClient.builder()
			.baseUrl(BASE_URL)
			.defaultHeader("User-Agent", USER_AGENT)
			.defaultHeader("Accept", "application/json")
			.build();
	}

	public OpenFoodFactsProductDto lookup(String barcode) {
		String normalized = barcode == null ? "" : barcode.trim();
		if (normalized.isEmpty() || !normalized.matches("\\d{6,14}")) {
			return OpenFoodFactsProductDto.invalidBarcode(normalized);
		}

		String responseBody;
		try {
			responseBody = restClient.get()
				.uri(uriBuilder -> uriBuilder
					.path("/api/v2/product/{barcode}")
					.queryParam("fields", FIELDS)
					.build(normalized))
				.retrieve()
				.body(String.class);
		} catch (Exception ex) {
			return OpenFoodFactsProductDto.notFound(normalized);
		}

		if (responseBody == null || responseBody.isBlank()) {
			return OpenFoodFactsProductDto.notFound(normalized);
		}

		JsonNode root;
		try {
			root = objectMapper.readTree(responseBody);
		} catch (Exception ex) {
			return OpenFoodFactsProductDto.notFound(normalized);
		}

		if (root.path("status").asInt() != 1) {
			return OpenFoodFactsProductDto.notFound(normalized);
		}

		JsonNode product = root.path("product");

		String name = firstNonBlank(
			text(product, "product_name_es"),
			text(product, "product_name"),
			text(product, "generic_name")
		);

		String description = firstNonBlank(
			text(product, "generic_name"),
			text(product, "product_name")
		);

		return OpenFoodFactsProductDto.found(
			normalized,
			name,
			description,
			text(product, "brands"),
			text(product, "quantity"),
			text(product, "categories"),
			text(product, "image_url")
		);
	}

	private static String text(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		String value = node.path(field).asText(null);
		return isBlank(value) ? null : value.trim();
	}

	private static String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (!isBlank(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
