package com.uniabsence.backend.dto;

public record StudentResponse(
	Long id,
	Long userId,
	String studentNumber,
	String firstName,
	String lastName,
	String email,
	String groupId,
	String program,
	String avatar,
	String academicYear,
	String createdAt
) {
}
