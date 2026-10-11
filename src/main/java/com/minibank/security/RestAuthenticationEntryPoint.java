package com.minibank.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.minibank.web.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(),
                "Username or password is missing or incorrect", request.getRequestURI());

        response.setStatus(status.value());
        response.setHeader("WWW-Authenticate", "Basic realm=\"MiniBank\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
