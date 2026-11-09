package com.uniabsence.backend.dto;

public record LoginRequest(String email, String password, String role) {
}
