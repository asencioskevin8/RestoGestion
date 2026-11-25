package com.restogestion.controller;

import com.restogestion.entity.Producto;
import com.restogestion.repository.CategoriaRepository;
import com.restogestion.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/menu")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @GetMapping
    public String listarMenu(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        return "menu/index";
    }

    @GetMapping("/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        model.addAttribute("producto", new Producto());
        model.addAttribute("categorias", categoriaRepository.findByActivoTrueOrderByOrdenMenuAsc());
        return "menu/form";
    }

    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable("id") Long id, Model model) {
        Producto producto = productoService.obtenerPorId(id);
        if (producto == null) {
            return "redirect:/menu";
        }
        model.addAttribute("producto", producto);
        model.addAttribute("categorias", categoriaRepository.findByActivoTrueOrderByOrdenMenuAsc());
        return "menu/form";
    }

    @PostMapping("/guardar")
    public String guardarProducto(@Valid @ModelAttribute("producto") Producto producto,
                                  BindingResult resultado,
                                  @RequestParam("categoriaId") Long categoriaId,
                                  Model model) {
        if (categoriaId != null) {
            categoriaRepository.findById(categoriaId).ifPresent(producto::setCategoria);
        }
        if (producto.getCategoria() == null) {
            resultado.rejectValue("categoria", "producto.categoria", "La categoría es obligatoria");
        }
        // @Valid valida la relación categoria antes de que el controlador la asigne
        // desde categoriaId. Ese error aislado no debe impedir el guardado.
        boolean hayErroresReales = !resultado.getGlobalErrors().isEmpty()
                || resultado.getFieldErrors().stream()
                .anyMatch(error -> !"categoria".equals(error.getField()));
        if (hayErroresReales || producto.getCategoria() == null) {
            model.addAttribute("categorias", categoriaRepository.findByActivoTrueOrderByOrdenMenuAsc());
            return "menu/form";
        }
        producto.setDisponible(producto.getStockActual() != null && producto.getStockActual().compareTo(java.math.BigDecimal.ZERO) > 0);
        productoService.guardar(producto);
        return "redirect:/menu";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable("id") Long id) {
        productoService.eliminar(id);
        return "redirect:/menu";
    }
}
