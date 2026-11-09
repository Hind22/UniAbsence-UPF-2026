package com.uniabsence.backend.dto;

public record StudentRequest(
	Long userId,
	String studentNumber,
	String firstName,
	String lastName,
	String email,
	String groupId,
	String program,
	String avatar,
	String academicYear
) {
}
