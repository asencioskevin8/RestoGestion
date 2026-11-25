package com.restogestion.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.restogestion.entity.Pedido;
import com.restogestion.repository.CategoriaRepository;
import com.restogestion.service.MesaService;
import com.restogestion.service.PedidoService;
import com.restogestion.service.ProductoService;

import java.math.BigDecimal;
import java.util.ArrayList;

@Controller
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final MesaService mesaService;
    private final ProductoService productoService;
    private final CategoriaRepository categoriaRepository;

    public PedidoController(PedidoService pedidoService, MesaService mesaService, ProductoService productoService,
                            CategoriaRepository categoriaRepository) {
        this.pedidoService = pedidoService;
        this.mesaService = mesaService;
        this.productoService = productoService;
        this.categoriaRepository = categoriaRepository;
    }
    //Muestra el listado de pedidos registrados.
    @GetMapping
    public String listarPedidos(@RequestParam(name = "estado", required = false, defaultValue = "TODOS") String estado,
                                Model model, Authentication authentication) {
        String filtro = estado == null ? "TODOS" : estado.toUpperCase();
        List<Pedido> pedidos = "TODOS".equals(filtro)
                ? pedidoService.listarPedidosActivos()
                : pedidoService.listarPedidosPorEstado(filtro);
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("estadoFiltro", filtro);
        model.addAttribute("puedeCrearPedido", tieneRol(authentication, "ROLE_ADMINISTRADOR")
                || tieneRol(authentication, "ROLE_MESERO"));
        return "pedidos/index";
    }

    private boolean tieneRol(Authentication authentication, String rol) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> rol.equals(authority.getAuthority()));
    }

    // Muestra el formulario para tomar un nuevo pedido/comanda.
    @GetMapping("/nuevo")
    public String nuevoPedidoForm(@RequestParam(name = "numeroMesa", required = false) String numeroMesa,
                                  Model model, RedirectAttributes flash) {
        if (numeroMesa == null || numeroMesa.isBlank()) {
            flash.addFlashAttribute("error", "Selecciona una mesa antes de registrar la comanda.");
            return "redirect:/mesas";
        }
        var mesa = mesaService.obtenerPorNumero(numeroMesa).orElse(null);
        if (mesa == null) {
            flash.addFlashAttribute("error", "Mesa no encontrada: " + numeroMesa);
            return "redirect:/mesas";
        }
        model.addAttribute("mesaSeleccionada", mesa);
        model.addAttribute("productos", productoService.listarTodos());
        model.addAttribute("categorias", categoriaRepository.findAll());
        return "pedidos/form";
    }

    //Procesa la creación manual del pedido con validación de stock y cálculo de totales.
    @PostMapping("/guardar")
    public String guardarPedido(@RequestParam("numeroMesa") String numeroMesa,
                                @RequestParam(name = "origen", defaultValue = "SALON") String origen,
                                @RequestParam(name = "observaciones", required = false) String observaciones,
                                @RequestParam(name = "productoId") List<Long> productoIds,
                                @RequestParam(name = "cantidad") List<BigDecimal> cantidades,
                                @RequestParam(name = "notas", required = false) List<String> notas,
                                RedirectAttributes flash) {
        try {
            List<PedidoService.DetalleItemDTO> items = new ArrayList<>();
            for (int i = 0; i < productoIds.size(); i++) {
                String nota = notas != null && i < notas.size() ? notas.get(i) : null;
                items.add(new PedidoService.DetalleItemDTO(productoIds.get(i), cantidades.get(i), nota));
            }
            Pedido pedidoGuardado = pedidoService.registrarComanda(numeroMesa, origen, observaciones, items);
            flash.addFlashAttribute("mensaje", "Pedido #" + pedidoGuardado.getIdPedido() + " creado con éxito en Mesa " + numeroMesa + ".");
            return "redirect:/pedidos";
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/pedidos/nuevo";
        }
    }
}
