package com.restogestion.controller;

import com.restogestion.entity.ComprobantePago;
import com.restogestion.entity.SesionCaja;
import com.restogestion.repository.ComprobantePagoRepository;
import com.restogestion.repository.SesionCajaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/reportes")
public class ReporteController {
    private final ComprobantePagoRepository comprobanteRepository;
    private final SesionCajaRepository sesionRepository;

    public ReporteController(ComprobantePagoRepository comprobanteRepository, SesionCajaRepository sesionRepository) {
        this.comprobanteRepository = comprobanteRepository;
        this.sesionRepository = sesionRepository;
    }

    @GetMapping
    public String reportes(Model model) {
        List<ComprobantePago> comprobantes = comprobanteRepository.findAll();
        BigDecimal efectivo = sumarPorMetodo(comprobantes, "EFECTIVO");
        BigDecimal tarjeta = sumarPorMetodo(comprobantes, "TARJETA");
        BigDecimal yape = sumarPorMetodo(comprobantes, "YAPE");
        BigDecimal plin = sumarPorMetodo(comprobantes, "PLIN");
        BigDecimal total = efectivo.add(tarjeta).add(yape).add(plin);
        model.addAttribute("comprobantes", comprobantes);
        model.addAttribute("sesiones", sesionRepository.findAll());
        model.addAttribute("efectivo", efectivo);
        model.addAttribute("tarjeta", tarjeta);
        model.addAttribute("yape", yape);
        model.addAttribute("plin", plin);
        model.addAttribute("total", total);
        return "reportes/index";
    }

    private BigDecimal sumarPorMetodo(List<ComprobantePago> comprobantes, String metodo) {
        return comprobantes.stream()
                .filter(c -> c.getMetodoPago() != null && metodo.equalsIgnoreCase(c.getMetodoPago().getNombreMetodo()))
                .map(ComprobantePago::getImporteTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
