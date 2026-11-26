package com.restogestion.service;

import com.restogestion.entity.Mesa;
import com.restogestion.repository.MesaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MesaServiceTest {
    private MesaRepository repository;
    private MesaService service;
    private Mesa mesa;

    @BeforeEach
    void preparar() {
        repository = mock(MesaRepository.class);
        service = new MesaService(repository);
        mesa = new Mesa("M01", 4);
        mesa.setIdMesa(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(mesa));
        when(repository.save(any(Mesa.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void aperturaRespetaCapacidadYRegistraComensales() {
        assertThrows(IllegalArgumentException.class, () -> service.abrirMesa(1L, 5));
        assertThrows(IllegalArgumentException.class, () -> service.abrirMesa(1L, 0));
        assertEquals("LIBRE", mesa.getEstado());
        service.abrirMesa(1L, 3);
        assertEquals("OCUPADA", mesa.getEstado());
        assertEquals(3, mesa.getComensalesActuales());
        assertNotNull(mesa.getFechaApertura());
        assertThrows(IllegalStateException.class, () -> service.abrirMesa(1L, 2));
    }

    @Test
    void noPermiteLiberarMesaOcupadaDesdeSalon() {
        service.abrirMesa(1L, 2);
        assertThrows(IllegalStateException.class, () -> service.transicionarEstado(1L, "LIBRE"));
        assertEquals("OCUPADA", mesa.getEstado());
        assertThrows(IllegalArgumentException.class, () -> service.transicionarEstado(1L, "INVALIDO"));
    }

    @Test
    void limpiezaLiberaAsientosYHoraDeApertura() {
        service.abrirMesa(1L, 2);
        mesa.setEstado("LIMPIEZA");
        service.transicionarEstado(1L, "LIBRE");
        assertEquals(0, mesa.getComensalesActuales());
        assertNull(mesa.getFechaApertura());
    }

    @Test
    void registroNormalizaCodigoYEvitaDuplicados() {
        when(repository.findByNumeroMesa("M01")).thenReturn(Optional.of(mesa));
        assertThrows(IllegalArgumentException.class, () -> service.guardar(new Mesa(" m01 ", 4)));
        assertThrows(IllegalArgumentException.class, () -> service.guardar(new Mesa(" ", 4)));
        Mesa nueva = service.guardar(new Mesa(" m02 ", 6));
        assertEquals("M02", nueva.getNumeroMesa());
        assertEquals("LIBRE", nueva.getEstado());
        assertEquals(0, nueva.getComensalesActuales());
    }
}
