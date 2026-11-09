package com.uniabsence.backend.dto;

public record PlannedSessionResponse(
	Long id,
	String groupId,
	String academicYear,
	String sessionDate,
	String startTime,
	String endTime,
	String subject,
	String sessionType,
	Long teacherId,
	String teacherName,
	String room,
	String status,
	String createdAt
) {
}
