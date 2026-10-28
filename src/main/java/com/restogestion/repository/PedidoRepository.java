package com.restogestion.repository;

import com.restogestion.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByEstadoPedido(String estadoPedido);
    List<Pedido> findByEstadoPedidoIn(List<String> estadosPedido);
    List<Pedido> findByMesaIdMesaAndEstadoPedidoNot(Long idMesa, String estadoCancelado);
    Optional<Pedido> findFirstByMesaIdMesaAndEstadoPedidoInOrderByFechaHoraDesc(Long idMesa, List<String> estados);
}
