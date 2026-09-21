package com.mockinterview.service;

import com.mockinterview.model.AppUser;
import com.mockinterview.repo.UserRepository;
import com.mockinterview.security.JwtService;
import com.mockinterview.web.Dtos;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public Dtos.AuthResponse register(Dtos.RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email already has an account.");
        }
        AppUser user = new AppUser();
        user.setName(req.name().trim());
        user.setEmail(req.email().trim().toLowerCase());
        user.setPasswordHash(encoder.encode(req.password()));
        users.save(user);
        return new Dtos.AuthResponse(jwt.createToken(user.getEmail()), user.getName(), user.getEmail());
    }

    public Dtos.AuthResponse login(Dtos.LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is wrong."));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is wrong.");
        }
        return new Dtos.AuthResponse(jwt.createToken(user.getEmail()), user.getName(), user.getEmail());
    }

    public AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
    }
}
