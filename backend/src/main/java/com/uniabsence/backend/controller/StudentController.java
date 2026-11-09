package com.uniabsence.backend.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uniabsence.backend.auth.AuthenticatedUser;
import com.uniabsence.backend.config.AuthInterceptor;
import com.uniabsence.backend.dto.StudentRequest;
import com.uniabsence.backend.dto.StudentResponse;
import com.uniabsence.backend.service.AuthorizationService;
import com.uniabsence.backend.service.StudentService;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

	private final StudentService studentService;
	private final AuthorizationService authorizationService;

	public StudentController(StudentService studentService, AuthorizationService authorizationService) {
		this.studentService = studentService;
		this.authorizationService = authorizationService;
	}

	@GetMapping
	public List<StudentResponse> getStudents(
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherProfileId(user);
			return studentService.findForTeacherUserId(user.id());
		}
		return studentService.findAll();
	}

	@GetMapping("/me")
	public StudentResponse getMyProfile(
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		return studentService.findById(authorizationService.requireStudentProfileId(user));
	}

	@GetMapping("/{id}")
	public StudentResponse getStudent(
			@PathVariable long id,
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		if ("STUDENT".equals(user.role())) {
			authorizationService.requireStudentAccessToStudent(user, id);
		} else if ("TEACHER".equals(user.role())) {
			authorizationService.requireTeacherAccessToStudent(user, id);
		}
		return studentService.findById(id);
	}

	@PostMapping
	public ResponseEntity<StudentResponse> createStudent(@RequestBody StudentRequest request) {
		StudentResponse student = studentService.create(request);
		return ResponseEntity
			.created(URI.create("/api/v1/students/" + student.id()))
			.body(student);
	}

	@PutMapping("/{id}")
	public StudentResponse updateStudent(
			@PathVariable long id,
			@RequestBody StudentRequest request) {
		return studentService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteStudent(@PathVariable long id) {
		studentService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
