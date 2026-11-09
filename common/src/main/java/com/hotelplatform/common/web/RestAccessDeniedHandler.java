package com.hotelplatform.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

// 403 в json, когда прав не хватает
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final GlobalExceptionHandler errorWriter;

    public RestAccessDeniedHandler(GlobalExceptionHandler errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.writeError(response, HttpStatus.FORBIDDEN,
                "Недостаточно прав для этой операции", request.getRequestURI());
    }
}
