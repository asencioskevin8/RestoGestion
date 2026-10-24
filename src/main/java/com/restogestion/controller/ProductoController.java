package com.restogestion.controller;

import com.restogestion.entity.Producto;
import com.restogestion.service.CategoriaService;
import com.restogestion.service.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/menu")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @Autowired(required = false)
    private CategoriaService categoriaService;

    @GetMapping
    public String listarMenu(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        return "menu/index";
    }

    @GetMapping("/nuevo")
    public String mostrarFormularioNuevo(Model model) {
        model.addAttribute("producto", new Producto());
        if (categoriaService != null) {
            model.addAttribute("categorias", categoriaService.listarTodas());
        }
        return "menu/form";
    }

    @GetMapping("/editar/{id}")
    public String mostrarFormularioEditar(@PathVariable("id") Long id, Model model) {
        Producto producto = productoService.obtenerPorId(id);
        if (producto == null) {
            return "redirect:/menu";
        }
        model.addAttribute("producto", producto);
        if (categoriaService != null) {
            model.addAttribute("categorias", categoriaService.listarTodas());
        }
        return "menu/form";
    }

    @PostMapping("/guardar")
    public String guardarProducto(@ModelAttribute("producto") Producto producto) {
        productoService.guardar(producto);
        return "redirect:/menu";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable("id") Long id) {
        productoService.eliminar(id);
        return "redirect:/menu";
    }
}