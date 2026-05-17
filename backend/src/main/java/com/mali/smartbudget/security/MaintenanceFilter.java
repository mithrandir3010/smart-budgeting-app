package com.mali.smartbudget.security;

import com.mali.smartbudget.service.SystemSettingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {

    private final SystemSettingService systemSettingService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Auth, admin and public-settings endpoints always pass through
        if (path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/admin")
                || path.startsWith("/api/v1/settings/public")
                || path.startsWith("/actuator")
                || path.equals("/health")) {
            chain.doFilter(request, response);
            return;
        }

        if (systemSettingService.isMaintenanceMode()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"maintenance\",\"message\":\"Sistem şu an bakımda. Lütfen kısa süre sonra tekrar deneyin.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
