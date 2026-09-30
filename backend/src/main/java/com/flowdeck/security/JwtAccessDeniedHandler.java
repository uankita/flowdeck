package com.flowdeck.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Same rationale as {@link JwtAuthenticationEntryPoint}, for the 403 case: an
 * authenticated caller whose {@code @PreAuthorize} check (e.g. an
 * insufficient workspace role) failed. {@code AccessDeniedException} is
 * thrown by the method-security interceptor before the controller method
 * runs, and — like an {@code AuthenticationException} — is caught by Spring
 * Security's filter chain rather than reaching {@code GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
        problem.setTitle("Forbidden");

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
