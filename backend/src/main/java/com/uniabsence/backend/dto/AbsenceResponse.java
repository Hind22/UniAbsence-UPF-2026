package com.uniabsence.backend.dto;

public record AbsenceResponse(
	Long id,
	Long studentId,
	Long teacherId,
	String absenceDate,
	String subject,
	String teacherName,
	String sessionLabel,
	String sessionType,
	String startTime,
	String endTime,
	String status,
	String createdAt
) {
}
