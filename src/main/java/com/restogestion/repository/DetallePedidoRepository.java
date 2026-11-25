package com.restogestion.repository;

import com.restogestion.entity.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Long> {
    List<DetallePedido> findByPedidoIdPedido(Long idPedido);
    List<DetallePedido> findByEstadoCocinaInOrderByPedidoFechaHoraAsc(List<String> estadosCocina);
}
