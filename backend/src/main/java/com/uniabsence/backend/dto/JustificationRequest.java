package com.uniabsence.backend.dto;

public record JustificationRequest(
	Long absenceId,
	String fileName,
	String filePath
) {
}
