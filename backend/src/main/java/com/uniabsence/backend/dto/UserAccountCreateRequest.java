package com.uniabsence.backend.dto;

public record UserAccountCreateRequest(
	String profileType,
	Long profileId,
	String initialPassword
) {
}
