package com.restogestion.controller;

import com.restogestion.entity.Usuario;
import com.restogestion.repository.RolRepository;
import com.restogestion.repository.UsuarioRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UsuarioApiController {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;

    public UsuarioApiController(UsuarioRepository usuarios, RolRepository roles) {
        this.usuarios = usuarios;
        this.roles = roles;
    }

    // La respuesta omite deliberadamente contraseñas y hashes.
    public record UsuarioVista(Long id, String nombre, String username, String rol, String estado) {}
    public record RolVista(Long id, String nombre) {}

    @GetMapping("/usuarios")
    @Transactional(readOnly = true)
    public List<UsuarioVista> activos() {
        return usuarios.findByEstado("ACTIVO").stream()
                .sorted(Comparator.comparing(Usuario::getNombreCompleto, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new UsuarioVista(u.getIdUsuario(), u.getNombreCompleto(), u.getUsername(),
                        u.getRol() == null ? "Sin rol" : u.getRol().getNombre(), u.getEstado()))
                .toList();
    }

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    public List<RolVista> rolesActivos() {
        return roles.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getActivo()))
                .map(r -> new RolVista(r.getIdRol(), r.getNombre()))
                .sorted(Comparator.comparing(RolVista::nombre))
                .toList();
    }
}
