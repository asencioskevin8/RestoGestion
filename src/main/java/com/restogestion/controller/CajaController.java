package com.restogestion.controller;

import com.restogestion.entity.ComprobantePago;
import com.restogestion.entity.SesionCaja;
import com.restogestion.service.CajaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Optional;

@Controller
@RequestMapping("/caja")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    // Panel principal de caja: estado de la sesión, resumen y mesas por cobrar
    @GetMapping
    public String panel(Model model) {
        SesionCaja sesion = cajaService.obtenerSesionActiva().orElse(null);
        model.addAttribute("sesion", sesion);
        if (sesion != null) {
            model.addAttribute("resumen", cajaService.resumirSesion(sesion));
            model.addAttribute("comprobantes", cajaService.listarComprobantesDeSesion(sesion.getIdSesion()));
        }
        model.addAttribute("mesas", cajaService.listarMesasPorCobrar());
        return "caja/index";
    }

    // RF-04: abrir caja
    @PostMapping("/abrir")
    public String abrirCaja(@RequestParam("montoInicial") BigDecimal montoInicial, RedirectAttributes flash) {
        try {
            cajaService.abrirCaja(montoInicial);
            flash.addFlashAttribute("exito", "Caja abierta correctamente con S/ " + montoInicial.setScale(2) + " de fondo inicial.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/caja";
    }

    // RF-11: pantalla de cobro de una mesa
    @GetMapping("/cobrar/{numeroMesa}")
    public String formularioCobro(@PathVariable String numeroMesa, Model model, RedirectAttributes flash) {
        try {
            model.addAttribute("cuenta", cajaService.obtenerCuenta(numeroMesa));
            model.addAttribute("metodos", cajaService.listarMetodosPago());
            model.addAttribute("sesionAbierta", cajaService.obtenerSesionActiva().isPresent());
            return "caja/cobro";
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/caja";
        }
    }

    @PostMapping("/cobrar")
    public String procesarCobro(@RequestParam String numeroMesa,
                                @RequestParam String tipoComprobante,
                                @RequestParam String metodoPago,
                                @RequestParam(required = false) BigDecimal montoRecibido,
                                RedirectAttributes flash) {
        try {
            ComprobantePago comprobante = cajaService.registrarCobro(numeroMesa, tipoComprobante, metodoPago, montoRecibido);
            return "redirect:/caja/comprobante/" + comprobante.getIdComprobante();
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/caja/cobrar/" + numeroMesa;
        }
    }

    // Comprobante emitido (imprimible)
    @GetMapping("/comprobante/{id}")
    public String verComprobante(@PathVariable Long id, Model model, RedirectAttributes flash) {
        Optional<ComprobantePago> comprobante = cajaService.obtenerComprobante(id);
        if (comprobante.isEmpty()) {
            flash.addFlashAttribute("error", "Comprobante no encontrado.");
            return "redirect:/caja";
        }
        model.addAttribute("c", comprobante.get());
        model.addAttribute("items", cajaService.obtenerItemsComprobante(comprobante.get()));
        return "caja/comprobante";
    }

    // RF-12: arqueo y cierre
    @GetMapping("/cierre")
    public String formularioCierre(Model model, RedirectAttributes flash) {
        SesionCaja sesion = cajaService.obtenerSesionActiva().orElse(null);
        if (sesion == null) {
            flash.addFlashAttribute("error", "No hay una sesión de caja abierta para cerrar.");
            return "redirect:/caja";
        }
        model.addAttribute("sesion", sesion);
        model.addAttribute("resumen", cajaService.resumirSesion(sesion));
        model.addAttribute("mesasPendientes", cajaService.contarMesasPendientesDeCobro());
        return "caja/cierre";
    }

    @PostMapping("/cierre")
    public String cerrarCaja(@RequestParam Long idSesion,
                             @RequestParam BigDecimal saldoFinalFisico,
                             RedirectAttributes flash) {
        try {
            SesionCaja cerrada = cajaService.cerrarCaja(idSesion, saldoFinalFisico);
            flash.addFlashAttribute("exito", "Caja cerrada. Descuadre registrado: S/ " + cerrada.getDescuadre());
            return "redirect:/caja/historial";
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/caja/cierre";
        }
    }

    @GetMapping("/historial")
    public String historial(Model model) {
        model.addAttribute("sesiones", cajaService.listarSesiones());
        return "caja/historial";
    }
}
