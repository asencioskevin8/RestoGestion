package com.restogestion.controller;

import com.restogestion.repository.CategoriaRepository;
import com.restogestion.repository.MesaRepository;
import com.restogestion.repository.ProductoRepository;
import com.restogestion.service.PedidoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/cliente")
public class ClienteController {
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MesaRepository mesaRepository;
    private final PedidoService pedidoService;

    public ClienteController(ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
                             MesaRepository mesaRepository, PedidoService pedidoService) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.mesaRepository = mesaRepository;
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public String menu(Model model) {
        model.addAttribute("productos", productoRepository.findAll());
        model.addAttribute("categorias", categoriaRepository.findByActivoTrueOrderByOrdenMenuAsc());
        model.addAttribute("mesas", mesaRepository.findAllByOrderByNumeroMesaAsc());
        return "cliente/index";
    }

    @PostMapping("/pedido")
    public String pedido(@RequestParam String numeroMesa, @RequestParam Long productoId,
                         @RequestParam BigDecimal cantidad, @RequestParam String metodoPago,
                         @RequestParam(required = false) String observaciones, RedirectAttributes flash) {
        try {
            String notas = "Método de pago solicitado: " + metodoPago;
            pedidoService.registrarComanda(numeroMesa, "CLIENTE", observaciones,
                    List.of(new PedidoService.DetalleItemDTO(productoId, cantidad, notas)));
            flash.addFlashAttribute("mensaje", "Pedido enviado correctamente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cliente";
    }
}
