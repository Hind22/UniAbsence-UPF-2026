package com.uniabsence.backend.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.uniabsence.backend.dto.UserAccountCreateRequest;
import com.uniabsence.backend.dto.UserAccountResponse;
import com.uniabsence.backend.dto.UserAccountUpdateRequest;
import com.uniabsence.backend.repository.UserRepository;
import com.uniabsence.backend.repository.UserRepository.ProfileAlreadyHasAccountException;
import com.uniabsence.backend.repository.UserRepository.ProfileNotFoundException;
import com.uniabsence.backend.repository.UserRepository.ProtectedAccountException;

@Service
public class UserService {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public List<UserAccountResponse> findAccounts() {
		try {
			return userRepository.findStudentAndTeacherAccounts();
		} catch (SQLException exception) {
			throw databaseError(exception);
		}
	}

	public UserAccountResponse createAccount(UserAccountCreateRequest request) {
		if (request == null || request.profileType() == null || request.profileId() == null
				|| request.profileId() <= 0) {
			throw badRequest("Le type et l'identifiant du profil sont obligatoires.");
		}
		String profileType = request.profileType().trim().toUpperCase(Locale.ROOT);
		if (!"STUDENT".equals(profileType) && !"TEACHER".equals(profileType)) {
			throw badRequest("Un compte ne peut être associé qu'à un profil étudiant ou enseignant.");
		}
		String initialPassword = validatePassword(request.initialPassword());

		try {
			return userRepository.createLinkedAccount(
				profileType,
				request.profileId(),
				passwordEncoder.encode(initialPassword)
			);
		} catch (ProfileNotFoundException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
		} catch (ProfileAlreadyHasAccountException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
		} catch (SQLException exception) {
			throw constraintError(exception);
		}
	}

	public UserAccountResponse updateAccount(long userId, UserAccountUpdateRequest request) {
		if (request == null || (isBlank(request.email()) && isBlank(request.newPassword()))) {
			throw badRequest("Fournissez une nouvelle adresse e-mail ou un mot de passe.");
		}
		String email = request.email() == null ? null : normalizeEmail(request.email());
		String passwordHash = request.newPassword() == null
			? null
			: passwordEncoder.encode(validatePassword(request.newPassword()));
		try {
			return userRepository.updateLinkedAccount(userId, email, passwordHash)
				.orElseThrow(() -> new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"Compte étudiant ou enseignant introuvable."
				));
		} catch (ProtectedAccountException exception) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
		} catch (SQLException exception) {
			throw constraintError(exception);
		}
	}

	private String validatePassword(String password) {
		if (password == null || password.length() < 8
				|| password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
			throw badRequest("Le mot de passe doit comporter au moins 8 caractères et au maximum 72 octets UTF-8.");
		}
		return password;
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw badRequest("L'adresse e-mail est obligatoire.");
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		if (normalized.length() > 320 || !EMAIL_PATTERN.matcher(normalized).matches()) {
			throw badRequest("L'adresse e-mail n'est pas valide.");
		}
		return normalized;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private ResponseStatusException constraintError(SQLException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
		if (message.contains("unique constraint failed")) {
			return new ResponseStatusException(
				HttpStatus.CONFLICT,
				"Cette adresse e-mail possède déjà un compte ou est déjà utilisée.",
				exception
			);
		}
		if (message.contains("foreign key constraint failed")) {
			return new ResponseStatusException(
				HttpStatus.CONFLICT,
				"Le profil ne peut pas être associé à ce compte.",
				exception
			);
		}
		return databaseError(exception);
	}

	private ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private ResponseStatusException databaseError(SQLException exception) {
		return new ResponseStatusException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Une erreur est survenue lors de la gestion des comptes utilisateurs.",
			exception
		);
	}
}
