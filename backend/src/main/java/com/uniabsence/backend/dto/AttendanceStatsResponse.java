package com.uniabsence.backend.dto;

public record AttendanceStatsResponse(
	Long studentId,
	String groupId,
	String referencePeriodLabel,
	String startDate,
	String endDate,
	Integer plannedSessionsCount,
	Integer distinctMissedSessionsCount,
	Integer totalAbsencesCount,
	Integer justifiedAbsencesCount,
	Integer unjustifiedAbsencesCount,
	Double absenceRate,
	String explanation
) {
}
