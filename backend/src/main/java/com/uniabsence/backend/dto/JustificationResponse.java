package com.uniabsence.backend.dto;

public record JustificationResponse(
	Long id,
	Long absenceId,
	Long studentId,
	String fileName,
	String filePath,
	String status,
	String submittedAt,
	String reviewedAt,
	Long reviewedByUserId
) {
}
