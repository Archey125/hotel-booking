package com.hotelplatform.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

// 401 в json, когда токена нет или он невалидный
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final GlobalExceptionHandler errorWriter;

    public RestAuthenticationEntryPoint(GlobalExceptionHandler errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        errorWriter.writeError(response, HttpStatus.UNAUTHORIZED,
                "Требуется аутентификация (некорректный или истёкший JWT)", request.getRequestURI());
    }
}
