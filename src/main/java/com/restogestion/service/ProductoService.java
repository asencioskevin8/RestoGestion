package com.restogestion.service;

import com.restogestion.entity.Categoria;
import com.restogestion.entity.Producto;
import com.restogestion.repository.CategoriaRepository;
import com.restogestion.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;

    public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
    }

    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    public List<Producto> listarDisponibles() {
        return productoRepository.findByDisponibleTrue();
    }

    public List<Producto> listarPorCategoria(Long idCategoria) {
        return productoRepository.findByCategoriaIdCategoriaAndDisponibleTrue(idCategoria);
    }

    public List<Categoria> listarCategoriasActivas() {
        return categoriaRepository.findByActivoTrueOrderByOrdenMenuAsc();
    }

    public List<Producto> obtenerProductosBajoStock() {
        return productoRepository.findProductosStockBajo();
    }

    public Optional<Producto> obtenerPorId(Long id) {
        return productoRepository.findById(id);
    }

    @Transactional
    public Producto guardarProducto(Producto producto) {
        if (producto.getStockActual() == null) {
            producto.setStockActual(BigDecimal.ZERO);
        }
        producto.setDisponible(producto.getStockActual().compareTo(BigDecimal.ZERO) > 0);
        return productoRepository.save(producto);
    }

    @Transactional
    public Producto ajustarStock(Long idProducto, int delta) {
        return productoRepository.findById(idProducto).map(p -> {
            BigDecimal nuevoStock = p.getStockActual().add(BigDecimal.valueOf(delta));
            if (nuevoStock.compareTo(BigDecimal.ZERO) < 0) {
                nuevoStock = BigDecimal.ZERO;
            }
            p.setStockActual(nuevoStock);
            p.setDisponible(nuevoStock.compareTo(BigDecimal.ZERO) > 0);
            return productoRepository.save(p);
        }).orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
    }
}
