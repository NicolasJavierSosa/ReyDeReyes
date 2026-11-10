package com.example.demo.dto;

import java.util.UUID;

public record ClienteDto(
	UUID id,
	String firstName,
	String lastName,
	String phone,
	String email,
	boolean active
) {}
