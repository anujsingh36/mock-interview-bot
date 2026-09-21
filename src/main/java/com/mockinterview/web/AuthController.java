package com.mockinterview.web;

import com.mockinterview.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public Dtos.AuthResponse register(@Valid @RequestBody Dtos.RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public Dtos.AuthResponse login(@Valid @RequestBody Dtos.LoginRequest req) {
        return auth.login(req);
    }
}
