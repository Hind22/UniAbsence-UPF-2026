package com.uniabsence.backend.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uniabsence.backend.dto.UserAccountCreateRequest;
import com.uniabsence.backend.dto.UserAccountResponse;
import com.uniabsence.backend.dto.UserAccountUpdateRequest;
import com.uniabsence.backend.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserAccountResponse> getAccounts() {
		return userService.findAccounts();
	}

	@PostMapping
	public ResponseEntity<UserAccountResponse> createAccount(@RequestBody UserAccountCreateRequest request) {
		UserAccountResponse account = userService.createAccount(request);
		return ResponseEntity
			.created(URI.create("/api/v1/users/" + account.id()))
			.body(account);
	}

	@PutMapping("/{id}")
	public UserAccountResponse updateAccount(
			@PathVariable long id,
			@RequestBody UserAccountUpdateRequest request) {
		return userService.updateAccount(id, request);
	}
}
