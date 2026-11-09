package com.uniabsence.backend.dto;

public record LoginResponse(
	String token,
	String tokenType,
	long expiresIn,
	String role,
	LoginUserResponse user
) {
}
