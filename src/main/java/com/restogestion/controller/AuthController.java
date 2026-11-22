package com.restogestion.controller;

import com.restogestion.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null
                || request.username().isBlank() || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Usuario y contraseña son obligatorios"));
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body(Map.of("error", "Usuario o contraseña incorrectos"));
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String token = jwtService.generateToken((org.springframework.security.core.userdetails.UserDetails)
                authentication.getPrincipal());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "tipo", "Bearer",
                "usuario", authentication.getName(),
                "roles", roles,
                "expiraEnMilisegundos", jwtService.getExpirationMs()
        ));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
