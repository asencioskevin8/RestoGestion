package com.restogestion.controller;

import com.restogestion.entity.Rol;
import com.restogestion.entity.Usuario;
import com.restogestion.repository.RolRepository;
import com.restogestion.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                             PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioRepository.findAll());
        return "usuarios/index";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("roles", rolRepository.findAll());
        return "usuarios/form";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes flash) {
        return usuarioRepository.findById(id).map(usuario -> {
            model.addAttribute("usuario", usuario);
            model.addAttribute("roles", rolRepository.findAll());
            return "usuarios/form";
        }).orElseGet(() -> {
            flash.addFlashAttribute("error", "Usuario no encontrado.");
            return "redirect:/usuarios";
        });
    }

    @PostMapping("/guardar")
    public String guardar(@RequestParam(required = false) Long idUsuario,
                          @RequestParam String nombreCompleto,
                          @RequestParam String username,
                          @RequestParam(required = false) String password,
                          @RequestParam Long rolId,
                          @RequestParam String estado,
                          RedirectAttributes flash) {
        try {
            Usuario usuario = idUsuario == null ? new Usuario() : usuarioRepository.findById(idUsuario)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
            usuarioRepository.findByUsername(username).ifPresent(otro -> {
                if (!otro.getIdUsuario().equals(usuario.getIdUsuario())) {
                    throw new IllegalArgumentException("El nombre de usuario ya existe.");
                }
            });
            if (idUsuario == null && (password == null || password.isBlank())) {
                throw new IllegalArgumentException("La contraseña es obligatoria para un usuario nuevo.");
            }
            Rol rol = rolRepository.findById(rolId).orElseThrow(() -> new IllegalArgumentException("Rol no válido."));
            usuario.setNombreCompleto(nombreCompleto.trim());
            usuario.setUsername(username.trim());
            usuario.setRol(rol);
            usuario.setEstado("ACTIVO".equalsIgnoreCase(estado) ? "ACTIVO" : "INACTIVO");
            if (password != null && !password.isBlank()) {
                usuario.setPasswordHash(passwordEncoder.encode(password));
            }
            usuarioRepository.save(usuario);
            flash.addFlashAttribute("mensaje", "Usuario guardado correctamente.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam String estado, RedirectAttributes flash) {
        usuarioRepository.findById(id).ifPresentOrElse(usuario -> {
            usuario.setEstado("ACTIVO".equalsIgnoreCase(estado) ? "ACTIVO" : "INACTIVO");
            usuarioRepository.save(usuario);
            flash.addFlashAttribute("mensaje", "Estado del usuario actualizado.");
        }, () -> flash.addFlashAttribute("error", "Usuario no encontrado."));
        return "redirect:/usuarios";
    }
}
