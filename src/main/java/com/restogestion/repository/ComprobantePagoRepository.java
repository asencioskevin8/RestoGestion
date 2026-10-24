package com.restogestion.repository;

import com.restogestion.entity.ComprobantePago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComprobantePagoRepository extends JpaRepository<ComprobantePago, Long> {
    Optional<ComprobantePago> findByPedidoIdPedido(Long idPedido);
    List<ComprobantePago> findBySesionCajaIdSesion(Long idSesion);

    @Query("SELECT COALESCE(MAX(c.numero), 0) FROM ComprobantePago c WHERE c.serie = :serie AND c.tipoComprobante = :tipoComprobante")
    Integer findUltimoNumeroPorSerie(String serie, String tipoComprobante);
}
