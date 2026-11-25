package com.restogestion.controller;

import com.restogestion.service.IdentityValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/clientes")
public class IdentidadController {

    private final IdentityValidationService identityValidationService;

    public IdentidadController(IdentityValidationService identityValidationService) {
        this.identityValidationService = identityValidationService;
    }

    @GetMapping("/validar")
    public ResponseEntity<?> validar(@RequestParam String tipo, @RequestParam String numero) {
        try {
            return ResponseEntity.ok(identityValidationService.validar(tipo, numero));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(Map.of("error", "No se pudo consultar la API de validación"));
        }
    }
}
