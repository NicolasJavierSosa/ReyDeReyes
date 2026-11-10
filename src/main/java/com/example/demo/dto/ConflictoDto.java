package com.example.demo.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConflictoDto(
	UUID id,
	String table,
	String entityId,
	String winner,
	String winnerData,
	String loserData,
	OffsetDateTime detectedAt,
	boolean reviewed
) {}
