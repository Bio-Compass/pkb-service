package com.biocompass.pkb.payload;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Command body admission runs only after workload and bearer authentication. */
public final class PkbJsonBodyFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/pkb/commands/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest bounded;
        try { bounded = PkbJsonRequestBody.bounded(request); }
        catch (PkbPayloadFailure failure) {
            response.setStatus(failure.status() == 422 ? 400 : failure.status()); response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"" + (failure.status() == 422 ? "pkb_invalid_command" : failure.code()) + "\",\"detail\":\"JSON contract rejected.\"}");
            return;
        }
        chain.doFilter(bounded, response);
    }
}
