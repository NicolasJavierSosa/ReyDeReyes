package com.example.demo.dto;

public record OpenFoodFactsProductDto(
	boolean found,
	String barcode,
	String name,
	String description,
	String brand,
	String quantity,
	String categories,
	String imageUrl,
	String reason
) {
	public static OpenFoodFactsProductDto found(
		String barcode,
		String name,
		String description,
		String brand,
		String quantity,
		String categories,
		String imageUrl
	) {
		return new OpenFoodFactsProductDto(true, barcode, name, description, brand, quantity, categories, imageUrl, null);
	}

	public static OpenFoodFactsProductDto notFound(String barcode) {
		return new OpenFoodFactsProductDto(false, barcode, null, null, null, null, null, null, "not_found");
	}

	public static OpenFoodFactsProductDto invalidBarcode(String barcode) {
		return new OpenFoodFactsProductDto(false, barcode, null, null, null, null, null, null, "invalid_barcode");
	}
}
