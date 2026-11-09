package com.uniabsence.backend.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uniabsence.backend.auth.AuthenticatedUser;
import com.uniabsence.backend.config.AuthInterceptor;
import com.uniabsence.backend.dto.TimetableResponse;
import com.uniabsence.backend.service.AuthorizationService;
import com.uniabsence.backend.service.TimetableService;

@RestController
@RequestMapping("/api/v1/students/me/timetable")
@Profile("dev")
public class TimetableController {

	private final AuthorizationService authorizationService;
	private final TimetableService timetableService;

	public TimetableController(
			AuthorizationService authorizationService,
			TimetableService timetableService) {
		this.authorizationService = authorizationService;
		this.timetableService = timetableService;
	}

	@GetMapping
	public TimetableResponse getMyTimetable(
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		long studentId = authorizationService.requireStudentProfileId(user);
		return timetableService.findForStudent(studentId);
	}
}
