package com.uniabsence.backend.dto;

import java.util.List;

public record GroupResponse(
	String id,
	String name,
	String specialty,
	String mainSubject,
	String academicYear,
	int studentCount,
	List<Long> teacherIds,
	String createdAt
) {
}
