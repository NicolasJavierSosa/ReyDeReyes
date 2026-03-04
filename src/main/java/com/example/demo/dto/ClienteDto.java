package com.example.demo.dto;

public record ClienteDto(
	Long id,
	String firstName,
	String lastName,
	String phone,
	String email,
	boolean active
) {}
