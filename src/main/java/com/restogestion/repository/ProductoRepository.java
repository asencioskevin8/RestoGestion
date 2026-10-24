package com.restogestion.repository;

import com.restogestion.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByDisponibleTrue();
    List<Producto> findByCategoriaIdCategoriaAndDisponibleTrue(Long idCategoria);

    @Query("SELECT p FROM Producto p WHERE p.disponible = true AND p.stockActual <= p.stockMinimo")
    List<Producto> findProductosStockBajo();
}
