package com.restogestion.repository;

import com.restogestion.entity.ComprobantePago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComprobantePagoRepository extends JpaRepository<ComprobantePago, Long> {
    Optional<ComprobantePago> findByPedidoIdPedido(Long idPedido);
    List<ComprobantePago> findBySesionCajaIdSesion(Long idSesion);

    @Query("SELECT DISTINCT c FROM ComprobantePago c "
            + "JOIN FETCH c.pedido p "
            + "LEFT JOIN FETCH p.mesa "
            + "LEFT JOIN FETCH p.detalles d "
            + "LEFT JOIN FETCH d.producto "
            + "JOIN FETCH c.metodoPago "
            + "WHERE c.idComprobante = :id")
    Optional<ComprobantePago> findComprobanteParaImpresion(@Param("id") Long id);

    @Query("SELECT COALESCE(MAX(c.numero), 0) FROM ComprobantePago c WHERE c.serie = :serie AND c.tipoComprobante = :tipoComprobante")
    Integer findUltimoNumeroPorSerie(String serie, String tipoComprobante);
}
