package com.uniabsence.backend.dto;

public record LoginUserResponse(
	long id,
	String email,
	String role,
	Long studentId,
	Long teacherId,
	String name
) {
}
