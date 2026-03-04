package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CierreCajaRequest(
	@NotNull @Size(min = 1) @Valid List<ConteoBilleteRequest> bills,
	String notes
) {}
