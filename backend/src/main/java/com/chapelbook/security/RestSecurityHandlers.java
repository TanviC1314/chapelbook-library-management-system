package com.chapelbook.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Security failures happen before controllers run, so they need their own JSON writers (401 and 403). */
@Component
public class RestSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public RestSecurityHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required to access this resource.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to do this.");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), pd);
    }
}
