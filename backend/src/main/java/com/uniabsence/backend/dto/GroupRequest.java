package com.uniabsence.backend.dto;

public record GroupRequest(
	String id,
	String name,
	String specialty,
	String mainSubject,
	String academicYear
) {
}
