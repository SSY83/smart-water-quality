package com.waterquality.controller;

import com.waterquality.dto.LoginRequest;
import com.waterquality.dto.Result;
import com.waterquality.service.AuthService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        return authService.logout(request);
    }
}
