package com.uniabsence.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuthTokenService {

	private static final String HEADER = Base64.getUrlEncoder().withoutPadding()
		.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final long TOKEN_LIFETIME_SECONDS = 3600;

	private final byte[] secret;
	private final ObjectMapper objectMapper;

	public AuthTokenService(
			@Value("${app.auth.token-secret}") String configuredSecret,
			ObjectMapper objectMapper) {
		this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
		if (secret.length < 32) {
			throw new IllegalArgumentException("app.auth.token-secret doit contenir au moins 32 octets.");
		}
		this.objectMapper = objectMapper;
	}

	public String createToken(AuthenticatedUser user) {
		long issuedAt = Instant.now().getEpochSecond();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("sub", user.email());
		claims.put("uid", user.id());
		claims.put("role", user.role());
		claims.put("iat", issuedAt);
		claims.put("exp", issuedAt + TOKEN_LIFETIME_SECONDS);

		try {
			String payload = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(objectMapper.writeValueAsBytes(claims));
			String unsignedToken = HEADER + "." + payload;
			return unsignedToken + "." + sign(unsignedToken);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Impossible de créer le jeton d'authentification.", exception);
		}
	}

	public AuthenticatedUser verify(String token) {
		if (token == null || token.length() > 8192) {
			throw new InvalidTokenException();
		}
		String[] segments = token.split("\\.", -1);
		if (segments.length != 3 || segments[0].isBlank() || segments[1].isBlank() || segments[2].isBlank()) {
			throw new InvalidTokenException();
		}

		String unsignedToken = segments[0] + "." + segments[1];
		byte[] suppliedSignature;
		try {
			suppliedSignature = Base64.getUrlDecoder().decode(segments[2]);
		} catch (IllegalArgumentException exception) {
			throw new InvalidTokenException();
		}
		byte[] expectedSignature = Base64.getUrlDecoder().decode(sign(unsignedToken));
		if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
			throw new InvalidTokenException();
		}

		try {
			JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(segments[0]));
			JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(segments[1]));
			long userId = claims.path("uid").asLong(0);
			long expiration = claims.path("exp").asLong(0);
			String email = claims.path("sub").asText("");
			String role = claims.path("role").asText("");
			if (!"HS256".equals(header.path("alg").asText())
					|| !"JWT".equals(header.path("typ").asText())
					|| userId <= 0
					|| email.isBlank()
					|| expiration <= Instant.now().getEpochSecond()
					|| !isKnownRole(role)) {
				throw new InvalidTokenException();
			}
			return new AuthenticatedUser(userId, email, role);
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new InvalidTokenException();
		}
	}

	public long tokenLifetimeSeconds() {
		return TOKEN_LIFETIME_SECONDS;
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
		} catch (java.security.GeneralSecurityException exception) {
			throw new IllegalStateException("Le mécanisme de signature HMAC-SHA256 est indisponible.", exception);
		}
	}

	private boolean isKnownRole(String role) {
		return "STUDENT".equals(role) || "TEACHER".equals(role) || "ADMIN".equals(role);
	}

	public static final class InvalidTokenException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
