package com.restogestion.controller;

import com.restogestion.repository.MesaRepository;
import com.restogestion.repository.ProductoRepository;
import com.restogestion.repository.SesionCajaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final MesaRepository mesaRepository;
    private final ProductoRepository productoRepository;
    private final SesionCajaRepository sesionCajaRepository;

    public HomeController(MesaRepository mesaRepository,
                          ProductoRepository productoRepository,
                          SesionCajaRepository sesionCajaRepository) {
        this.mesaRepository = mesaRepository;
        this.productoRepository = productoRepository;
        this.sesionCajaRepository = sesionCajaRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("totalMesas", mesaRepository.count());
        model.addAttribute("totalProductos", productoRepository.count());
        model.addAttribute("cajaAbierta", sesionCajaRepository.findFirstByEstadoOrderByFechaAperturaDesc("ABIERTA").isPresent());
        return "index";
    }
}
