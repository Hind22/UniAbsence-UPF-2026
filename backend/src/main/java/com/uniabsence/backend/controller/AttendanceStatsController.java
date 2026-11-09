package com.uniabsence.backend.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.auth.AuthenticatedUser;
import com.uniabsence.backend.config.AuthInterceptor;
import com.uniabsence.backend.dto.AttendanceStatsResponse;
import com.uniabsence.backend.dto.PlannedSessionRequest;
import com.uniabsence.backend.dto.PlannedSessionResponse;
import com.uniabsence.backend.service.AttendanceStatsService;
import com.uniabsence.backend.service.AuthorizationService;

@RestController
@RequestMapping("/api/v1")
public class AttendanceStatsController {

	private final AttendanceStatsService attendanceStatsService;
	private final AuthorizationService authorizationService;

	public AttendanceStatsController(
			AttendanceStatsService attendanceStatsService,
			AuthorizationService authorizationService) {
		this.attendanceStatsService = attendanceStatsService;
		this.authorizationService = authorizationService;
	}

	@GetMapping("/students/me/attendance-stats")
	public AttendanceStatsResponse getMyAttendanceStats(
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		long studentId = authorizationService.requireStudentProfileId(user);
		return attendanceStatsService.getStatsForStudent(studentId);
	}

	@GetMapping("/students/{id}/attendance-stats")
	public AttendanceStatsResponse getStudentAttendanceStats(
			@PathVariable long id,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("STUDENT".equals(user.role())) {
			authorizationService.requireStudentAccessToStudent(user, id);
		} else if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherAccessToStudent(user, id);
		}
		return attendanceStatsService.getStatsForStudent(id);
	}

	@GetMapping("/students/me/planned-sessions")
	public List<PlannedSessionResponse> getMyPlannedSessions(
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		long studentId = authorizationService.requireStudentProfileId(user);
		return attendanceStatsService.getPlannedSessionsForStudent(studentId);
	}

	@GetMapping("/planned-sessions")
	public List<PlannedSessionResponse> getPlannedSessions(
			@RequestParam(required = false) String groupId,
			@RequestParam(required = false) String academicYear,
			@RequestParam(required = false) String startDate,
			@RequestParam(required = false) String endDate,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("STUDENT".equals(user.role())) {
			long studentId = authorizationService.requireStudentProfileId(user);
			return attendanceStatsService.getPlannedSessionsForStudent(studentId);
		}
		return attendanceStatsService.getPlannedSessions(groupId, academicYear, startDate, endDate);
	}

	@PostMapping("/planned-sessions")
	public ResponseEntity<PlannedSessionResponse> createPlannedSession(
			@RequestBody PlannedSessionRequest request,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if (!"ADMIN".equals(user.role()) && !"TEACHER".equals(user.role())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seuls les administrateurs et enseignants peuvent ajouter des séances prévues.");
		}
		PlannedSessionResponse created = attendanceStatsService.createPlannedSession(request);
		return ResponseEntity.created(URI.create("/api/v1/planned-sessions/" + created.id())).body(created);
	}
}
