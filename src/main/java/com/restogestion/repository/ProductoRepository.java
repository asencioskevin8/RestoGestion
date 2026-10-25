package com.restogestion.repository;

import com.restogestion.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // Consulta para listar productos con poco stock (menor o igual a 5 unidades)
    @Query("SELECT p FROM Producto p WHERE p.stockActual <= 5")
    List<Producto> findProductosStockBajo();
}