package com.restogestion.service;

import com.restogestion.entity.DetallePedido;
import com.restogestion.entity.Pedido;
import com.restogestion.repository.DetallePedidoRepository;
import com.restogestion.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CocinaService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;

    public CocinaService(PedidoRepository pedidoRepository, DetallePedidoRepository detallePedidoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
    }

    public List<Pedido> obtenerComandasActivas() {
        List<Pedido> pendientes = pedidoRepository.findByEstadoPedido("PENDIENTE");
        pendientes.addAll(pedidoRepository.findByEstadoPedido("EN_PREPARACION"));
        return pendientes;
    }

    @Transactional
    public Pedido avanzarEstadoPedido(Long idPedido) {
        return pedidoRepository.findById(idPedido).map(pedido -> {
            if ("PENDIENTE".equalsIgnoreCase(pedido.getEstadoPedido())) {
                pedido.setEstadoPedido("EN_PREPARACION");
                for (DetallePedido d : pedido.getDetalles()) {
                    d.setEstadoCocina("EN_PREPARACION");
                }
            } else if ("EN_PREPARACION".equalsIgnoreCase(pedido.getEstadoPedido())) {
                pedido.setEstadoPedido("LISTO");
                for (DetallePedido d : pedido.getDetalles()) {
                    d.setEstadoCocina("LISTO");
                }
            } else {
                pedido.setEstadoPedido("ENTREGADO");
                for (DetallePedido d : pedido.getDetalles()) {
                    d.setEstadoCocina("ENTREGADO");
                }
            }
            return pedidoRepository.save(pedido);
        }).orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + idPedido));
    }
}
