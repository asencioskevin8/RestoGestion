package com.restogestion.service;

import com.restogestion.entity.DetallePedido;
import com.restogestion.entity.Mesa;
import com.restogestion.entity.Pedido;
import com.restogestion.entity.Producto;
import com.restogestion.entity.Usuario;
import com.restogestion.repository.MesaRepository;
import com.restogestion.repository.PedidoRepository;
import com.restogestion.repository.ProductoRepository;
import com.restogestion.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final MesaRepository mesaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         MesaRepository mesaRepository,
                         ProductoRepository productoRepository,
                         UsuarioRepository usuarioRepository) {
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<Pedido> listarPedidosPorEstado(String estado) {
        return pedidoRepository.findByEstadoPedido(estado);
    }

    @Transactional
    public Pedido registrarComanda(String numeroMesa, String origen, String observaciones, List<DetalleItemDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("La comanda debe contener al menos un producto");
        }
        Mesa mesa = mesaRepository.findByNumeroMesa(numeroMesa)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + numeroMesa));

        Usuario mesero = usuarioActual();

        Pedido pedido = new Pedido();
        pedido.setMesa(mesa);
        pedido.setUsuarioMesero(mesero);
        pedido.setOrigen(origen != null ? origen : "SALON");
        pedido.setEstadoPedido("PENDIENTE");
        pedido.setObservaciones(observaciones);

        BigDecimal total = BigDecimal.ZERO;

        for (DetalleItemDTO item : items) {
            if (item == null || item.idProducto == null || item.cantidad == null
                    || item.cantidad.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Cada detalle debe tener producto y una cantidad mayor a cero");
            }
            Producto prod = productoRepository.findById(item.idProducto)
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con ID: " + item.idProducto));

            if (!Boolean.TRUE.equals(prod.getDisponible())) {
                throw new IllegalStateException("Producto no disponible: " + prod.getNombreProducto());
            }

            if (Boolean.TRUE.equals(prod.getControlaStock())) {
                if (prod.getStockActual().compareTo(item.cantidad) < 0) {
                    throw new IllegalStateException("Stock insuficiente para: " + prod.getNombreProducto());
                }
                prod.setStockActual(prod.getStockActual().subtract(item.cantidad));
                if (prod.getStockActual().compareTo(BigDecimal.ZERO) <= 0) {
                    prod.setDisponible(false);
                }
                productoRepository.save(prod);
            }

            DetallePedido detalle = new DetallePedido();
            detalle.setProducto(prod);
            detalle.setCantidad(item.cantidad);
            detalle.setPrecioUnitario(prod.getPrecioUnitario());
            detalle.setSubtotal(prod.getPrecioUnitario().multiply(item.cantidad));
            detalle.setObservaciones(item.notas);
            detalle.setEstadoCocina("PENDIENTE");

            pedido.addDetalle(detalle);
            total = total.add(detalle.getSubtotal());
        }

        pedido.setTotalCalculado(total);
        pedidoRepository.save(pedido);

        mesa.setEstado("OCUPADA");
        if (mesa.getFechaApertura() == null) {
            mesa.setFechaApertura(LocalDateTime.now());
        }
        mesaRepository.save(mesa);

        return pedido;
    }

    private Usuario usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return usuarioRepository.findByUsername(authentication.getName()).orElse(null);
        }
        return usuarioRepository.findAll().stream().findFirst().orElse(null);
    }

    public static class DetalleItemDTO {
        public Long idProducto;
        public BigDecimal cantidad;
        public String notas;

        public DetalleItemDTO() {}

        public DetalleItemDTO(Long idProducto, BigDecimal cantidad, String notas) {
            this.idProducto = idProducto;
            this.cantidad = cantidad;
            this.notas = notas;
        }
    }
}
