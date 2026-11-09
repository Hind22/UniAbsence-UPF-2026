package com.uniabsence.backend.dto;

public record JustificationDecisionRequest(
	String status,
	Long reviewedByUserId
) {
}
