package com.uniabsence.backend.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.uniabsence.backend.auth.AuthenticatedUser;
import com.uniabsence.backend.config.AuthInterceptor;
import com.uniabsence.backend.dto.JustificationDecisionRequest;
import com.uniabsence.backend.dto.JustificationRequest;
import com.uniabsence.backend.dto.JustificationResponse;
import com.uniabsence.backend.service.AuthorizationService;
import com.uniabsence.backend.service.JustificationService;

@RestController
@RequestMapping("/api/v1/justifications")
public class JustificationController {

	private final JustificationService justificationService;
	private final AuthorizationService authorizationService;

	public JustificationController(
			JustificationService justificationService,
			AuthorizationService authorizationService) {
		this.justificationService = justificationService;
		this.authorizationService = authorizationService;
	}

	@GetMapping
	public List<JustificationResponse> getJustifications(
			@RequestParam(required = false) Long studentId,
			@RequestParam(required = false) Long absenceId,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("STUDENT".equals(user.role())) {
			authorizationService.requireStudentProfileId(user);
			if (studentId != null) {
				authorizationService.requireStudentAccessToStudent(user, studentId);
			}
			if (absenceId != null) {
				authorizationService.requireStudentOwnsAbsence(user, absenceId);
			}
			return studentId != null || absenceId != null
				? justificationService.findAll(studentId, absenceId)
				: justificationService.findForStudentUserId(user.id());
		}
		if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherProfileId(user);
			if (studentId != null) {
				authorizationService.requireTeacherAccessToStudent(user, studentId);
			}
			if (absenceId != null) {
				authorizationService.requireTeacherAccessToAbsence(user, absenceId);
			}
			return studentId != null || absenceId != null
				? justificationService.findAll(studentId, absenceId)
				: justificationService.findForTeacherUserId(user.id());
		}
		return justificationService.findAll(studentId, absenceId);
	}

	@GetMapping("/{id}")
	public JustificationResponse getJustification(
			@PathVariable long id,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		JustificationResponse justification = justificationService.findById(id);
		if ("STUDENT".equals(user.role())) {
			authorizationService.requireStudentAccessToJustification(user, justification);
		} else if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherAccessToJustification(user, justification);
		}
		return justification;
	}

	@PostMapping
	public ResponseEntity<JustificationResponse> createJustification(
			@RequestBody JustificationRequest request,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("STUDENT".equals(user.role()) && request != null && request.absenceId() != null) {
			authorizationService.requireStudentOwnsAbsence(user, request.absenceId());
		}
		JustificationResponse justification = justificationService.create(request);
		return ResponseEntity
			.created(URI.create("/api/v1/justifications/" + justification.id()))
			.body(justification);
	}

	@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<JustificationResponse> uploadJustification(
			@RequestParam Long absenceId,
			@RequestParam("file") MultipartFile file,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("STUDENT".equals(user.role())) {
			authorizationService.requireStudentOwnsAbsence(user, absenceId);
		}
		JustificationResponse justification = justificationService.upload(absenceId, file);
		return ResponseEntity
			.created(URI.create("/api/v1/justifications/" + justification.id()))
			.body(justification);
	}

	@GetMapping("/{id}/file")
	public ResponseEntity<ByteArrayResource> downloadJustificationFile(
			@PathVariable long id,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		JustificationResponse justification = justificationService.findById(id);
		if ("STUDENT".equals(user.role())) {
			authorizationService.requireStudentAccessToJustification(user, justification);
		} else if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherAccessToJustification(user, justification);
		}
		var file = justificationService.readFile(justification);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(file.contentType()))
			.contentLength(file.contents().length)
			.header(
				HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment()
					.filename(file.fileName(), StandardCharsets.UTF_8)
					.build()
					.toString()
			)
			.header("X-Content-Type-Options", "nosniff")
			.cacheControl(CacheControl.noStore().cachePrivate())
			.body(new ByteArrayResource(file.contents()));
	}

	@PutMapping("/{id}")
	public JustificationResponse updateJustification(
			@PathVariable long id,
			@RequestBody JustificationRequest request) {
		return justificationService.update(id, request);
	}

	@PatchMapping("/{id}/decision")
	public JustificationResponse decideJustification(
			@PathVariable long id,
			@RequestBody(required = false) JustificationDecisionRequest request,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherProfileId(user);
			JustificationResponse justification = justificationService.findById(id);
			authorizationService.requireTeacherAccessToJustification(user, justification);
		}
		JustificationDecisionRequest decision = request == null
			? null
			: new JustificationDecisionRequest(request.status(), user.id());
		return justificationService.decide(id, decision);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteJustification(@PathVariable long id) {
		justificationService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
