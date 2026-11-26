package com.restogestion.controller;

import com.restogestion.entity.Rol;
import com.restogestion.entity.Usuario;
import com.restogestion.repository.RolRepository;
import com.restogestion.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UsuarioApiControllerTest {
    @Test
    void listaCuentasActivasConRolSinExponerHash() throws Exception {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        RolRepository roles = mock(RolRepository.class);
        Rol rol = new Rol("ADMINISTRADOR", "Acceso completo");
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1L);
        usuario.setNombreCompleto("Kevin Asencios");
        usuario.setUsername("admin");
        usuario.setPasswordHash("hash-privado");
        usuario.setRol(rol);
        when(usuarios.findByEstado("ACTIVO")).thenReturn(List.of(usuario));

        MockMvcBuilders.standaloneSetup(new UsuarioApiController(usuarios, roles)).build()
                .perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Kevin Asencios"))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        verify(usuarios).findByEstado("ACTIVO");
        verify(usuarios, never()).findAll();
    }

    @Test
    void listaVaciaSeDevuelveComoArreglo() throws Exception {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.findByEstado("ACTIVO")).thenReturn(List.of());
        MockMvcBuilders.standaloneSetup(new UsuarioApiController(usuarios, mock(RolRepository.class))).build()
                .perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
