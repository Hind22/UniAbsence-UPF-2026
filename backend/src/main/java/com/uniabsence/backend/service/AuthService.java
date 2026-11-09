package com.uniabsence.backend.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.auth.AuthenticatedUser;
import com.uniabsence.backend.auth.AuthTokenService;
import com.uniabsence.backend.dto.LoginRequest;
import com.uniabsence.backend.dto.LoginResponse;
import com.uniabsence.backend.dto.LoginUserResponse;
import com.uniabsence.backend.repository.AuthRepository;
import com.uniabsence.backend.repository.AuthRepository.Account;

@Service
public class AuthService {

	private final AuthRepository authRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthTokenService tokenService;
	private final String dummyPasswordHash;

	public AuthService(
			AuthRepository authRepository,
			PasswordEncoder passwordEncoder,
			AuthTokenService tokenService) {
		this.authRepository = authRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	public LoginResponse login(LoginRequest request) {
		if (request == null || request.email() == null || request.email().isBlank()
				|| request.password() == null || request.password().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'adresse e-mail et le mot de passe sont obligatoires.");
		}
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		String password = request.password();
		if (email.length() > 320 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
				|| password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les identifiants fournis ne sont pas valides.");
		}

		Account account;
		try {
			account = authRepository.findByEmail(email).orElse(null);
		} catch (SQLException exception) {
			throw new ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Une erreur est survenue pendant l'authentification.",
				exception
			);
		}

		String passwordHash = account == null ? dummyPasswordHash : account.passwordHash();
		if (!passwordEncoder.matches(password, passwordHash) || account == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Adresse e-mail ou mot de passe incorrect.");
		}

		String role = mapRole(account.databaseRole());
		AuthenticatedUser authenticatedUser = new AuthenticatedUser(account.id(), account.email(), role);
		String name = account.firstName() == null
			? account.email()
			: (account.firstName() + " " + account.lastName()).trim();
		LoginUserResponse user = new LoginUserResponse(
			account.id(),
			account.email(),
			role,
			account.studentId(),
			account.teacherId(),
			name
		);
		return new LoginResponse(
			tokenService.createToken(authenticatedUser),
			"Bearer",
			tokenService.tokenLifetimeSeconds(),
			role,
			user
		);
	}

	private String mapRole(String databaseRole) {
		return switch (databaseRole) {
			case "student" -> "STUDENT";
			case "teacher" -> "TEACHER";
			case "administrator" -> "ADMIN";
			default -> throw new IllegalStateException("Rôle utilisateur inconnu dans la base de données.");
		};
	}
}
