package com.uniabsence.backend.dto;

public record UserAccountUpdateRequest(
	String email,
	String newPassword
) {
}
