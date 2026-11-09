package com.uniabsence.backend.dto;

import java.util.List;

public record TimetableResponse(
	String groupId,
	String groupName,
	String academicYear,
	boolean demonstration,
	List<Session> sessions
) {
	public record Session(
		long id,
		int dayOfWeek,
		String dayName,
		String subject,
		String startTime,
		String endTime,
		String sessionType,
		String teacherName,
		String room,
		boolean demonstration
	) {
	}
}
