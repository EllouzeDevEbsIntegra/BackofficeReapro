package com.reapro.achat.Controller;

import com.reapro.achat.DTO.*;
import com.reapro.achat.services.AuthService;
import com.reapro.achat.services.PermissionService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"api/auth", "auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PermissionService permissionService;

    /**
     * Validation de session. Route protégée :
     *  - 200 si authentifié (token valide) → renvoie email + rôles + permissions effectives ;
     *  - 401 si non authentifié (géré par l'authenticationEntryPoint).
     * Aucun rôle métier requis → utilisable par le frontend pour distinguer 403 droits vs session invalide.
     */
    @GetMapping("/me")
    public SessionInfoResponse me(Authentication authentication) {
        String email = String.valueOf(authentication.getName());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        ResolvedPermissions resolved = permissionService.resolveForEmail(email);
        return new SessionInfoResponse(true, email, roles, resolved.superAdmin(), resolved.permissions());
    }

    @PostMapping("/register")
    public String register(@RequestBody RegisterRequest req){
        return authService.register(req);
    }

    @PostMapping("/verify-register-code")
    public String verifyRegister(@RequestBody VerifyCodeRequest req){
        return authService.verifyRegisterCode(req);
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest req){
        return authService.login(req);
    }

    @PostMapping("/refresh-token")
    public TokenResponse refresh(@RequestParam String refreshToken){
        return authService.refreshToken(refreshToken);
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestBody ForgotPasswordRequest req){
        return authService.forgotPassword(req);
    }

    @PostMapping("/verify-reset-code")
    public String verifyResetCode(@RequestBody VerifyCodeRequest req) throws MessagingException {
        return authService.verifyResetCode(req);
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String email,
                                 @RequestBody ChangePasswordRequest req) throws MessagingException {
        return authService.changePassword(email, req);
    }
}
