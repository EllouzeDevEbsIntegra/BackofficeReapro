package com.reapro.achat.config;

import com.reapro.achat.repositories.primary.AdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final AdminRepository adminRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String email = jwtUtils.extractEmailFromAccessToken(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (adminRepository.existsByEmail(email)) {

                    // Récupère le rôle depuis la base (enum → on convertit en String avec .name())
                    String roleName = adminRepository.findByEmail(email)
                            .map(admin -> admin.getRole().name())  // ← .name() pour enum → String
                            .orElse("ROLE_ADMIN");

                    var auth = new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            java.util.List.of(new SimpleGrantedAuthority(roleName))
                    );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

}
