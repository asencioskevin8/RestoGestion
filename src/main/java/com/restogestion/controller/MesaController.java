package com.restogestion.controller;

import com.restogestion.entity.Mesa;
import com.restogestion.service.MesaService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RF-05 · Salón y Mapa de Mesas.
 * Vistas Thymeleaf en templates/mesas/ (mapa del salón y formulario de mesa).
 */
@Controller
@RequestMapping("/mesas")
public class MesaController {

    private final MesaService mesaService;

    public MesaController(MesaService mesaService) {
        this.mesaService = mesaService;
    }

    /** El formulario solo puede enviar estos campos; estado y comensales los controla el servicio. */
    @InitBinder("mesa")
    public void configurarBinder(WebDataBinder binder) {
        binder.setAllowedFields("idMesa", "numeroMesa", "capacidad");
    }

    // ----------------------------------------------------------
    // MAPA DEL SALÓN
    // ----------------------------------------------------------
    @GetMapping
    public String mapa(@RequestParam(name = "estado", required = false) String estado, Model model) {
        String filtro = mesaService.esEstadoValido(estado) ? estado.toUpperCase() : null;

        Map<String, String> etiquetas = new LinkedHashMap<>();
        etiquetas.put("LIBRE", "Libre");
        etiquetas.put("OCUPADA", "Ocupada");
        etiquetas.put("RESERVADA", "Reservada");
        etiquetas.put("CUENTA", "En cuenta");
        etiquetas.put("LIMPIEZA", "Limpieza");

        model.addAttribute("mesas", mesaService.listarPorEstado(filtro));
        model.addAttribute("conteo", mesaService.contarPorEstado());
        model.addAttribute("resumen", mesaService.resumen());
        model.addAttribute("filtro", filtro);
        model.addAttribute("etiquetas", etiquetas);
        return "mesas/index";
    }

    // ----------------------------------------------------------
    // REGISTRO Y EDICIÓN DE MESAS
    // ----------------------------------------------------------
    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("mesa", new Mesa());
        model.addAttribute("capacidadMaxima", MesaService.CAPACIDAD_MAXIMA);
        return "mesas/form";
    }

    @GetMapping("/editar/{id}")
    public String editar(@PathVariable("id") Long id, Model model, RedirectAttributes flash) {
        return mesaService.obtenerPorId(id).map(mesa -> {
            model.addAttribute("mesa", mesa);
            model.addAttribute("capacidadMaxima", MesaService.CAPACIDAD_MAXIMA);
            return "mesas/form";
        }).orElseGet(() -> {
            flash.addFlashAttribute("error", "La mesa solicitada no existe.");
            return "redirect:/mesas";
        });
    }

    @PostMapping("/guardar")
    public String guardar(@Valid @ModelAttribute("mesa") Mesa mesa, BindingResult resultado,
                          Model model, RedirectAttributes flash) {
        model.addAttribute("capacidadMaxima", MesaService.CAPACIDAD_MAXIMA);
        if (resultado.hasErrors()) {
            return "mesas/form";
        }
        try {
            boolean esNueva = mesa.getIdMesa() == null;
            Mesa guardada = mesaService.guardar(mesa);
            flash.addFlashAttribute("mensaje", "Mesa " + guardada.getNumeroMesa()
                    + (esNueva ? " registrada correctamente." : " actualizada correctamente."));
            return "redirect:/mesas";
        } catch (IllegalArgumentException | IllegalStateException e) {
            resultado.reject("mesa.invalida", e.getMessage());
            return "mesas/form";
        }
    }

    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable("id") Long id,
                           @RequestParam(name = "filtro", required = false) String filtro,
                           RedirectAttributes flash) {
        try {
            mesaService.eliminar(id);
            flash.addFlashAttribute("mensaje", "Mesa eliminada correctamente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volver(filtro);
    }

    // ----------------------------------------------------------
    // OPERACIÓN DEL SALÓN (estado y comensales)
    // ----------------------------------------------------------
    @PostMapping("/{id}/abrir")
    public String abrir(@PathVariable("id") Long id,
                        @RequestParam(name = "comensales", defaultValue = "0") int comensales,
                        @RequestParam(name = "filtro", required = false) String filtro,
                        RedirectAttributes flash) {
        try {
            Mesa mesa = mesaService.abrirMesa(id, comensales);
            flash.addFlashAttribute("mensaje", "Mesa " + mesa.getNumeroMesa()
                    + " abierta con " + mesa.getComensalesActuales() + " comensales.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volver(filtro);
    }

    @PostMapping("/{id}/comensales")
    public String comensales(@PathVariable("id") Long id,
                             @RequestParam(name = "comensales", defaultValue = "0") int comensales,
                             @RequestParam(name = "filtro", required = false) String filtro,
                             RedirectAttributes flash) {
        try {
            Mesa mesa = mesaService.actualizarComensales(id, comensales);
            flash.addFlashAttribute("mensaje", "Mesa " + mesa.getNumeroMesa()
                    + ": " + mesa.getComensalesActuales() + " comensales registrados.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volver(filtro);
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable("id") Long id,
                                @RequestParam("estado") String estado,
                                @RequestParam(name = "filtro", required = false) String filtro,
                                RedirectAttributes flash) {
        try {
            Mesa mesa = mesaService.transicionarEstado(id, estado);
            flash.addFlashAttribute("mensaje", "Mesa " + mesa.getNumeroMesa()
                    + " ahora está en estado " + mesa.getEstado() + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volver(filtro);
    }

    /** Regresa al mapa conservando el filtro de estado (solo si es un estado válido). */
    private String volver(String filtro) {
        return mesaService.esEstadoValido(filtro)
                ? "redirect:/mesas?estado=" + filtro.toUpperCase()
                : "redirect:/mesas";
    }
}
