package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambioClaveRequest(
	@NotBlank String currentPassword,
	@NotBlank @Size(min = 6) String newPassword
) {}
