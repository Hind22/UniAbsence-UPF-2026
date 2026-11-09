package com.uniabsence.backend.dto;

public record PlannedSessionRequest(
	String groupId,
	String academicYear,
	String sessionDate,
	String startTime,
	String endTime,
	String subject,
	String sessionType,
	Long teacherId,
	String room,
	String status
) {
}
