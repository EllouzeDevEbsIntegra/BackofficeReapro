package com.reapro.achat.config;

import com.reapro.achat.repositories.primary.AdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final AdminRepository adminRepository;

    /**
     * CORRECTIF 403 : les endpoints renvoyant un type réactif ({@code Mono}/{@code Flux}) sont
     * traités en ASYNC par Spring MVC. Par défaut, {@link OncePerRequestFilter} ne s'exécute PAS
     * sur le dispatch ASYNC ({@code shouldNotFilterAsyncDispatch()} = true) → le SecurityContext
     * n'est pas repeuplé lors de l'écriture du résultat → {@code .authenticated()} refuse → 403.
     * On force donc l'exécution du filtre JWT aussi sur le dispatch ASYNC.
     * (Ex. {@code GET /api/v1/sync-adaptable} renvoie {@code Mono} ; {@code /api/compare-quotes}
     * est synchrone et n'était donc pas impacté.)
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String email = jwtUtils.extractEmailFromAccessToken(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // SÉCURITÉ : un token techniquement valide ne suffit pas. L'utilisateur doit toujours
                // exister ET être actif. Un compte désactivé (ou supprimé) ne peut donc plus utiliser
                // un JWT encore valide → contexte laissé vide → 401 via l'authenticationEntryPoint.
                adminRepository.findByEmail(email)
                        .filter(admin -> admin.isActive())
                        .ifPresent(admin -> {
                            // Rôle depuis la base (enum → String). Défaut prudent : ROLE_USER si absent.
                            String roleName = admin.getRole() != null ? admin.getRole().name() : "ROLE_USER";

                            var auth = new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    java.util.List.of(new SimpleGrantedAuthority(roleName))
                            );
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            }
        }

        // Log de diagnostic SÛR (jamais de token/cookie) : chemin, type de dispatch, auth présente, authorities.
        if (log.isDebugEnabled()) {
            var current = SecurityContextHolder.getContext().getAuthentication();
            log.debug("[JwtFilter] path={} dispatch={} authPresent={} authorities={}",
                    request.getRequestURI(),
                    request.getDispatcherType(),
                    current != null,
                    current != null ? current.getAuthorities() : "[]");
        }

        filterChain.doFilter(request, response);
    }

}
