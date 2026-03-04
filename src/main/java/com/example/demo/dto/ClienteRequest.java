package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record ClienteRequest(
	@NotBlank String firstName,
	@NotBlank String lastName,
	@NotBlank String phone,
	@NotBlank String email
) {}
