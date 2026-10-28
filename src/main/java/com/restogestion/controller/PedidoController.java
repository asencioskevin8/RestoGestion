package com.restogestion.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.restogestion.entity.Pedido;
import com.restogestion.service.MesaService;
import com.restogestion.service.PedidoService;
import com.restogestion.service.ProductoService;

@Controller
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final MesaService mesaService;
    private final ProductoService productoService;

    public PedidoController(PedidoService pedidoService, MesaService mesaService, ProductoService productoService) {
        this.pedidoService = pedidoService;
        this.mesaService = mesaService;
        this.productoService = productoService;
    }
    //Muestra el listado de pedidos registrados.
    @GetMapping
    public String listarPedidos(@RequestParam(name = "estado", required = false, defaultValue = "PENDIENTE") String estado, Model model) {
        List<Pedido> pedidos = pedidoService.listarPedidosPorEstado(estado);
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("estadoFiltro", estado);
        return "pedidos/index";
    }

    // Muestra el formulario para tomar un nuevo pedido/comanda.
    @GetMapping("/nuevo")
    public String nuevoPedidoForm(Model model) {
        model.addAttribute("mesas", mesaService.listarPorEstado("LIBRE"));
        model.addAttribute("productos", productoService.listarTodos());
        return "pedidos/form";
    }

    //Procesa la creación manual del pedido con validación de stock y cálculo de totales.
    @PostMapping("/guardar")
    public String guardarPedido(@RequestParam("numeroMesa") String numeroMesa,
                                @RequestParam(name = "origen", defaultValue = "SALON") String origen,
                                @RequestParam(name = "observaciones", required = false) String observaciones,
                                @RequestBody List<PedidoService.DetalleItemDTO> items,
                                RedirectAttributes flash) {
        try {
            Pedido pedidoGuardado = pedidoService.registrarComanda(numeroMesa, origen, observaciones, items);
            flash.addFlashAttribute("mensaje", "Pedido #" + pedidoGuardado.getIdPedido() + " creado con éxito en Mesa " + numeroMesa + ".");
            return "redirect:/pedidos";
        } catch (IllegalArgumentException | IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/pedidos/nuevo";
        }
    }
}
