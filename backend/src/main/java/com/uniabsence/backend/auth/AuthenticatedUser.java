package com.uniabsence.backend.auth;

public record AuthenticatedUser(long id, String email, String role) {
}
