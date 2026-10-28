package com.restogestion.controller;

import com.restogestion.entity.Mesa;
import com.restogestion.entity.Pedido;
import com.restogestion.entity.SesionCaja;
import com.restogestion.repository.MesaRepository;
import com.restogestion.repository.PedidoRepository;
import com.restogestion.service.CajaService;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/caja")
public class CajaController {

    private final CajaService cajaService;
    private final MesaRepository mesaRepository;
    private final PedidoRepository pedidoRepository;

    public CajaController(CajaService cajaService, MesaRepository mesaRepository, PedidoRepository pedidoRepository) {
        this.cajaService = cajaService;
        this.mesaRepository = mesaRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @GetMapping
    public String resumen(Model model) {
        List<Mesa> mesas = mesaRepository.findByEstado("CUENTA");
        List<String> estadosActivos = List.of("PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO");
        Map<Long, Pedido> pedidosPorMesa = new HashMap<>();
        for (Mesa mesa : mesas) {
            pedidoRepository.findFirstByMesaIdMesaAndEstadoPedidoInOrderByFechaHoraDesc(mesa.getIdMesa(), estadosActivos)
                    .ifPresent(pedido -> pedidosPorMesa.put(mesa.getIdMesa(), pedido));
        }
        var sesion = cajaService.obtenerSesionActiva().orElse(null);
        model.addAttribute("sesion", sesion);
        model.addAttribute("saldoEsperado", sesion == null ? BigDecimal.ZERO : cajaService.calcularSaldoEsperado(sesion));
        model.addAttribute("mesas", mesas);
        model.addAttribute("pedidosPorMesa", pedidosPorMesa);
        model.addAttribute("pedidosActivos", pedidoRepository.findByEstadoPedidoIn(
                java.util.List.of("PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO")));
        return "caja/index";
    }

    @PostMapping("/cobrar")
    public String cobrar(@RequestParam String numeroMesa,
                         @RequestParam String tipoComprobante,
                         @RequestParam String metodoPago,
                         @RequestParam(required = false) BigDecimal montoRecibido,
                         RedirectAttributes flash) {
        try {
            var comprobante = cajaService.registrarCobro(numeroMesa, tipoComprobante, metodoPago, montoRecibido);
            flash.addFlashAttribute("mensaje", "Cobro registrado: " + comprobante.getSerie() + "-" + comprobante.getNumero());
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/caja";
    }

    @PostMapping("/apertura")
    public String abrir(@RequestParam BigDecimal montoInicial, Authentication authentication, RedirectAttributes flash) {
        try {
            cajaService.abrirCaja(montoInicial, authentication.getName());
            flash.addFlashAttribute("mensaje", "Caja abierta correctamente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/caja";
    }

    @PostMapping("/cierre")
    public String cerrar(@RequestParam Long idSesion,
                         @RequestParam(required = false) BigDecimal saldoFinalFisico,
                         RedirectAttributes flash) {
        try {
            SesionCaja sesion = cajaService.cerrarCaja(idSesion, saldoFinalFisico);
            flash.addFlashAttribute("mensaje", "Caja cerrada. Descuadre: S/ " + sesion.getDescuadre());
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/caja";
    }
}
