package com.uniabsence.backend.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uniabsence.backend.auth.AuthenticatedUser;
import com.uniabsence.backend.config.AuthInterceptor;
import com.uniabsence.backend.dto.AbsenceRequest;
import com.uniabsence.backend.dto.AbsenceResponse;
import com.uniabsence.backend.dto.TeacherResponse;
import com.uniabsence.backend.service.AuthorizationService;
import com.uniabsence.backend.service.AbsenceService;
import com.uniabsence.backend.service.TeacherService;

@RestController
@RequestMapping("/api/v1/absences")
public class AbsenceController {

	private final AbsenceService absenceService;
	private final AuthorizationService authorizationService;
	private final TeacherService teacherService;

	public AbsenceController(
			AbsenceService absenceService,
			AuthorizationService authorizationService,
			TeacherService teacherService) {
		this.absenceService = absenceService;
		this.authorizationService = authorizationService;
		this.teacherService = teacherService;
	}

	@GetMapping
	public List<AbsenceResponse> getAbsences(
			@RequestParam(required = false) Long studentId,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("STUDENT".equals(user.role())) {
			authorizationService.requireStudentProfileId(user);
			if (studentId != null) {
				authorizationService.requireStudentAccessToStudent(user, studentId);
				return absenceService.findByStudentId(studentId);
			}
			return absenceService.findForStudentUserId(user.id());
		}
		if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherProfileId(user);
			if (studentId != null) {
				authorizationService.requireTeacherAccessToStudent(user, studentId);
				return absenceService.findByStudentId(studentId);
			}
			return absenceService.findForTeacherUserId(user.id());
		}
		return studentId == null
			? absenceService.findAll()
			: absenceService.findByStudentId(studentId);
	}

	@GetMapping("/{id}")
	public AbsenceResponse getAbsence(
			@PathVariable long id,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		AbsenceResponse absence = absenceService.findById(id);
		if ("STUDENT".equals(user.role())) {
			authorizationService.requireStudentAccessToAbsence(user, absence);
		} else if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherAccessToAbsence(user, absence);
		}
		return absence;
	}

	@PostMapping
	public ResponseEntity<AbsenceResponse> createAbsence(
			@RequestBody AbsenceRequest request,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("TEACHER".equals(user.role())) {
			if (request == null || request.studentId() == null || request.studentId() <= 0) {
				throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"studentId doit être un entier positif."
				);
			}
			authorizationService.requireTeacherAccessToStudent(user, request.studentId());
			long teacherId = authorizationService.requireTeacherProfileId(user);
			TeacherResponse teacher = teacherService.findById(teacherId);
			request = new AbsenceRequest(
				request.studentId(),
				teacherId,
				request.absenceDate(),
				request.subject(),
				(teacher.firstName() + " " + teacher.lastName()).trim(),
				request.sessionLabel(),
				request.sessionType(),
				request.startTime(),
				request.endTime(),
				"unjustified"
			);
		}
		AbsenceResponse absence = absenceService.create(request);
		return ResponseEntity
			.created(URI.create("/api/v1/absences/" + absence.id()))
			.body(absence);
	}

	@PutMapping("/{id}")
	public AbsenceResponse updateAbsence(
			@PathVariable long id,
			@RequestBody AbsenceRequest request,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherAccessToAbsence(user, id);
			AbsenceResponse existing = absenceService.findById(id);
			if (request == null || request.studentId() == null || request.studentId() <= 0) {
				throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"studentId doit être un entier positif."
				);
			}
			authorizationService.requireTeacherAccessToStudent(user, request.studentId());
			long teacherId = authorizationService.requireTeacherProfileId(user);
			TeacherResponse teacher = teacherService.findById(teacherId);
			request = new AbsenceRequest(
				request.studentId(),
				teacherId,
				request.absenceDate(),
				request.subject(),
				(teacher.firstName() + " " + teacher.lastName()).trim(),
				request.sessionLabel(),
				request.sessionType(),
				request.startTime(),
				request.endTime(),
				existing.status()
			);
		}
		return absenceService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteAbsence(
			@PathVariable long id,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherAccessToAbsence(user, id);
		}
		absenceService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
