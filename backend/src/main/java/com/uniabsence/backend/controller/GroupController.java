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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uniabsence.backend.dto.GroupRequest;
import com.uniabsence.backend.dto.GroupResponse;
import com.uniabsence.backend.service.GroupService;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

	private final GroupService groupService;

	public GroupController(GroupService groupService) {
		this.groupService = groupService;
	}

	@GetMapping
	public List<GroupResponse> getGroups() {
		return groupService.findAll();
	}

	@GetMapping("/{id}")
	public GroupResponse getGroup(@PathVariable String id) {
		return groupService.findById(id);
	}

	@PostMapping
	public ResponseEntity<GroupResponse> createGroup(@RequestBody GroupRequest request) {
		GroupResponse group = groupService.create(request);
		return ResponseEntity
			.created(URI.create("/api/v1/groups/" + group.id()))
			.body(group);
	}

	@PutMapping("/{id}")
	public GroupResponse updateGroup(
			@PathVariable String id,
			@RequestBody GroupRequest request) {
		return groupService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
		groupService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
