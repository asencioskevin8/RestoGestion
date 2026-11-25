package com.restogestion.repository;

import com.restogestion.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // El umbral depende de cada producto y se almacena en stockMinimo.
    @Query("SELECT p FROM Producto p WHERE p.stockActual <= p.stockMinimo ORDER BY p.nombreProducto ASC")
    List<Producto> findProductosStockBajo();
}
