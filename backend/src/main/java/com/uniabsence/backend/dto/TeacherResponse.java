package com.uniabsence.backend.dto;

import java.util.List;

public record TeacherResponse(
	Long id,
	Long userId,
	String teacherIdentifier,
	String firstName,
	String lastName,
	String email,
	String subject,
	String grade,
	String department,
	String avatar,
	String academicYear,
	List<String> groupIds,
	String createdAt
) {
}
