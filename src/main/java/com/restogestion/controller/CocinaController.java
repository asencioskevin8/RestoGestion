package com.restogestion.controller;

import com.restogestion.entity.Pedido;
import com.restogestion.service.CocinaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/cocina")
public class CocinaController {

    private final CocinaService cocinaService;

    public CocinaController(CocinaService cocinaService) {
        this.cocinaService = cocinaService;
    }

    /**
     * Vista principal del Monitor de Cocina (KDS).
     */
    @GetMapping
    public String monitorCocina(Model model) {
        List<Pedido> comandasActivas = cocinaService.obtenerComandasActivas();

        long totalPendientes = comandasActivas.stream()
                .filter(p -> "PENDIENTE".equalsIgnoreCase(p.getEstadoPedido()))
                .count();
        long totalPreparacion = comandasActivas.stream()
                .filter(p -> "EN_PREPARACION".equalsIgnoreCase(p.getEstadoPedido()))
                .count();

        model.addAttribute("comandas", comandasActivas);
        model.addAttribute("totalPendientes", totalPendientes);
        model.addAttribute("totalPreparacion", totalPreparacion);

        return "cocina/index";
    }

    /**
     * Acción para avanzar el estado de la comanda desde el KDS.
     */
    @PostMapping("/pedidos/{id}/avanzar")
    public String avanzarPedido(@PathVariable("id") Long idPedido, RedirectAttributes flash) {
        try {
            Pedido pedido = cocinaService.avanzarEstadoPedido(idPedido);
            flash.addFlashAttribute("mensaje", "Comanda #" + pedido.getIdPedido() 
                    + " avanzada a estado: " + pedido.getEstadoPedido() + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cocina";
    }
}
