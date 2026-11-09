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
import com.uniabsence.backend.dto.GroupResponse;
import com.uniabsence.backend.dto.TeacherRequest;
import com.uniabsence.backend.dto.TeacherResponse;
import com.uniabsence.backend.service.AuthorizationService;
import com.uniabsence.backend.service.GroupService;
import com.uniabsence.backend.service.TeacherService;

@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

	private final TeacherService teacherService;
	private final GroupService groupService;
	private final AuthorizationService authorizationService;

	public TeacherController(
			TeacherService teacherService,
			GroupService groupService,
			AuthorizationService authorizationService) {
		this.teacherService = teacherService;
		this.groupService = groupService;
		this.authorizationService = authorizationService;
	}

	@GetMapping("/me")
	public TeacherResponse getMyProfile(
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		return teacherService.findById(authorizationService.requireTeacherProfileId(user));
	}

	@GetMapping("/me/groups")
	public List<GroupResponse> getMyGroups(
			@RequestAttribute(AuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
		authorizationService.requireTeacherProfileId(user);
		return groupService.findForTeacherUserId(user.id());
	}

	@GetMapping
	public List<TeacherResponse> getTeachers() {
		return teacherService.findAll();
	}

	@GetMapping("/{id}")
	public TeacherResponse getTeacher(@PathVariable long id) {
		return teacherService.findById(id);
	}

	@PostMapping
	public ResponseEntity<TeacherResponse> createTeacher(@RequestBody TeacherRequest request) {
		TeacherResponse teacher = teacherService.create(request);
		return ResponseEntity
			.created(URI.create("/api/v1/teachers/" + teacher.id()))
			.body(teacher);
	}

	@PutMapping("/{id}")
	public TeacherResponse updateTeacher(
			@PathVariable long id,
			@RequestBody TeacherRequest request) {
		return teacherService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteTeacher(@PathVariable long id) {
		teacherService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
