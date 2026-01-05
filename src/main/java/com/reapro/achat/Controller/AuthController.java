package com.reapro.achat.Controller;

import com.reapro.achat.DTO.*;
import com.reapro.achat.services.AuthService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
