package com.mealplanner.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Creates the CSRF token before the page renders.
 *
 * Spring Security creates the token lazily, the first time a template asks for it.
 * With database sessions, a long page (like login) can already have started
 * sending before that happens, and a session can't be created after that point.
 * Loading it here avoids that. Pages without forms are skipped so anonymous hits,
 * like the uptime monitor on /home, never create a session row.
 */
public class CsrfEagerLoadFilter extends OncePerRequestFilter {

    private static final Set<String> SKIP = Set.of("/", "/home", "/about", "/error");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean staticFile = path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/webjars/") || path.startsWith("/images/");
        if (!staticFile && !SKIP.contains(path)) {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
        }
        chain.doFilter(request, response);
    }
}
