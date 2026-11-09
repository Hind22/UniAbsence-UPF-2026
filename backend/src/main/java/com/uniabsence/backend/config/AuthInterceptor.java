package com.uniabsence.backend.config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.uniabsence.backend.auth.AuthTokenService;
import com.uniabsence.backend.auth.AuthTokenService.InvalidTokenException;
import com.uniabsence.backend.auth.AuthenticatedUser;

@Component
public class AuthInterceptor implements HandlerInterceptor {

	public static final String AUTHENTICATED_USER_ATTRIBUTE = "authenticatedUser";

	private final AuthTokenService tokenService;

	public AuthInterceptor(AuthTokenService tokenService) {
		this.tokenService = tokenService;
	}

	@Override
	public boolean preHandle(
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler) throws IOException {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}

		List<String> authorizationHeaders = Collections.list(request.getHeaders("Authorization"));
		if (authorizationHeaders.size() != 1) {
			return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentification requise.");
		}
		String authorization = authorizationHeaders.getFirst();
		if (!authorization.startsWith("Bearer ") || authorization.length() <= "Bearer ".length()) {
			return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentification requise.");
		}

		AuthenticatedUser user;
		try {
			user = tokenService.verify(authorization.substring("Bearer ".length()));
		} catch (InvalidTokenException exception) {
			return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Jeton invalide ou expiré.");
		}
		request.setAttribute(AUTHENTICATED_USER_ATTRIBUTE, user);

		if (!isAllowed(user.role(), request.getMethod(), request.getRequestURI())) {
			return reject(response, HttpServletResponse.SC_FORBIDDEN, "Ce compte ne peut pas effectuer cette action.");
		}
		return true;
	}

	private boolean isAllowed(String role, String method, String path) {
		if ("ADMIN".equals(role)) {
			return true;
		}

		if ("STUDENT".equals(role)) {
			return ("GET".equals(method) && (
					"/api/v1/students/me".equals(path)
					|| "/api/v1/students/me/timetable".equals(path)
					|| "/api/v1/students/me/attendance-stats".equals(path)
					|| "/api/v1/students/me/planned-sessions".equals(path)
					|| "/api/v1/planned-sessions".equals(path)
					|| path.matches("/api/v1/students/\\d+")
					|| path.matches("/api/v1/students/\\d+/attendance-stats")
					|| path.matches("/api/v1/(absences|justifications)(/\\d+)?")
					|| path.matches("/api/v1/justifications/\\d+/file")))
				|| ("POST".equals(method) && (
					"/api/v1/justifications".equals(path)
					|| "/api/v1/justifications/upload".equals(path)));
		}
		if ("TEACHER".equals(role)) {
			return ("GET".equals(method) && (
					"/api/v1/teachers/me".equals(path)
					|| "/api/v1/teachers/me/groups".equals(path)
					|| "/api/v1/students".equals(path)
					|| path.matches("/api/v1/students/\\d+")
					|| path.matches("/api/v1/students/\\d+/attendance-stats")
					|| path.matches("/api/v1/(absences|justifications)(/\\d+)?")
					|| path.matches("/api/v1/justifications/\\d+/file")))
				|| ("POST".equals(method) && "/api/v1/absences".equals(path))
				|| ("PUT".equals(method) && path.matches("/api/v1/absences/\\d+"))
				|| ("DELETE".equals(method) && path.matches("/api/v1/absences/\\d+"))
				|| ("PATCH".equals(method) && path.matches("/api/v1/justifications/\\d+/decision"));
		}
		return false;
	}

	private boolean reject(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setHeader("WWW-Authenticate", "Bearer");
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"message\":\"" + message + "\"}");
		return false;
	}
}
