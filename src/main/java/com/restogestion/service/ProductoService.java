package com.restogestion.service;

import com.restogestion.entity.Producto;
import com.restogestion.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id).orElse(null);
    }

    @Transactional
    public Producto guardar(Producto producto) {
        return productoRepository.save(producto);
    }

    @Transactional
    public void eliminar(Long id) {
        productoRepository.deleteById(id);
    }

    @Transactional
    public void actualizarStock(Long idProducto, Integer cantidad) {
        Producto producto = obtenerPorId(idProducto);
        if (producto != null && producto.getStock() != null) {
            int nuevoStock = producto.getStock() - cantidad;
            producto.setStock(Math.max(nuevoStock, 0));
            productoRepository.save(producto);
        }
    }
}