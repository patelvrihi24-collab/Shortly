package org.v.shortly.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

// NOTE: deliberately NOT annotated with @Component (explained below the code).
public class JwtAuthFilter extends OncePerRequestFilter {   // runs once per request, before controllers

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");          // expected: "Bearer eyJhbGci..."
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);                       // no token: continue as anonymous
            return;
        }
        try {
            AuthenticatedUser user = jwtService.parse(header.substring(7));   // cut off "Bearer "
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, List.of()));
            // Tells Spring "this request is authenticated as <user>". The 3-argument constructor marks it authenticated.
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            HttpErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            return;                                                  // stop here: a bad token is an error, not "anonymous"
        }
        chain.doFilter(request, response);                           // pass the request on to the next filter/controller
    }
}