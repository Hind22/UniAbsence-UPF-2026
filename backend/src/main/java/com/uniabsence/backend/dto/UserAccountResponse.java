package com.uniabsence.backend.dto;

public record UserAccountResponse(
	long id,
	String email,
	String role,
	String profileType,
	long profileId,
	String name
) {
}
