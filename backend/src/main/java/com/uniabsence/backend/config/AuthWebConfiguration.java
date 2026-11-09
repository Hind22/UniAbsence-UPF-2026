package com.uniabsence.backend.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfiguration implements WebMvcConfigurer {

	private final AuthInterceptor authInterceptor;

	@Value("${app.cors.allowed-origin-patterns:}")
	private String[] allowedOriginPatterns;

	public AuthWebConfiguration(AuthInterceptor authInterceptor) {
		this.authInterceptor = authInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authInterceptor)
			.addPathPatterns("/api/v1/**")
			.excludePathPatterns("/api/v1/auth/login");
	}

	@Override
	public void addCorsMappings(
			org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
		CorsRegistration cors = registry.addMapping("/api/v1/**");
		if (Arrays.stream(allowedOriginPatterns).anyMatch(origin -> !origin.isBlank())) {
			cors.allowedOriginPatterns(allowedOriginPatterns);
		}
		cors.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("Authorization", "Content-Type")
			.allowCredentials(false)
			.maxAge(3600);
	}
}
