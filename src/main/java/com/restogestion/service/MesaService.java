package com.restogestion.service;

import com.restogestion.entity.Mesa;
import com.restogestion.repository.MesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MesaService {

    private final MesaRepository mesaRepository;

    public MesaService(MesaRepository mesaRepository) {
        this.mesaRepository = mesaRepository;
    }

    public List<Mesa> listarTodas() {
        return mesaRepository.findAllByOrderByNumeroMesaAsc();
    }

    public Optional<Mesa> obtenerPorId(Long id) {
        return mesaRepository.findById(id);
    }

    public Optional<Mesa> obtenerPorNumero(String numeroMesa) {
        return mesaRepository.findByNumeroMesa(numeroMesa);
    }

    @Transactional
    public Mesa cambiarEstado(Long id, String nuevoEstado) {
        return mesaRepository.findById(id).map(mesa -> {
            mesa.setEstado(nuevoEstado);
            if ("LIBRE".equalsIgnoreCase(nuevoEstado)) {
                mesa.setComensalesActuales(0);
                mesa.setFechaApertura(null);
            } else if ("OCUPADA".equalsIgnoreCase(nuevoEstado) && mesa.getFechaApertura() == null) {
                mesa.setFechaApertura(LocalDateTime.now());
            }
            return mesaRepository.save(mesa);
        }).orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada con ID: " + id));
    }

    @Transactional
    public Mesa asignarComensales(Long id, int comensales) {
        return mesaRepository.findById(id).map(mesa -> {
            if (comensales > mesa.getCapacidad()) {
                throw new IllegalArgumentException("El número de comensales supera la capacidad máxima de " + mesa.getCapacidad());
            }
            mesa.setComensalesActuales(comensales);
            return mesaRepository.save(mesa);
        }).orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
    }
}
