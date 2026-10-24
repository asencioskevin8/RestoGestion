package com.restogestion.controller;

import com.restogestion.entity.*;
import com.restogestion.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {

    private final MesaRepository mesaRepository;
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final SesionCajaRepository sesionCajaRepository;
    private final ComprobantePagoRepository comprobantePagoRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final UsuarioRepository usuarioRepository;

    public ApiController(MesaRepository mesaRepository,
                         ProductoRepository productoRepository,
                         CategoriaRepository categoriaRepository,
                         PedidoRepository pedidoRepository,
                         DetallePedidoRepository detallePedidoRepository,
                         SesionCajaRepository sesionCajaRepository,
                         ComprobantePagoRepository comprobantePagoRepository,
                         MetodoPagoRepository metodoPagoRepository,
                         UsuarioRepository usuarioRepository) {
        this.mesaRepository = mesaRepository;
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.sesionCajaRepository = sesionCajaRepository;
        this.comprobantePagoRepository = comprobantePagoRepository;
        this.metodoPagoRepository = metodoPagoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ----------------------------------------------------------
    // MESAS
    // ----------------------------------------------------------
    @GetMapping("/mesas")
    public List<Mesa> getMesas() {
        return mesaRepository.findAllByOrderByNumeroMesaAsc();
    }

    @PutMapping("/mesas/{id}/estado")
    public ResponseEntity<?> updateEstadoMesa(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return mesaRepository.findById(id).map(mesa -> {
            String nuevoEstado = body.get("estado");
            if (nuevoEstado != null) {
                mesa.setEstado(nuevoEstado);
                if ("LIBRE".equalsIgnoreCase(nuevoEstado)) {
                    mesa.setComensalesActuales(0);
                    mesa.setFechaApertura(null);
                } else if ("OCUPADA".equalsIgnoreCase(nuevoEstado) && mesa.getFechaApertura() == null) {
                    mesa.setFechaApertura(LocalDateTime.now());
                }
                mesaRepository.save(mesa);
            }
            return ResponseEntity.ok(mesa);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ----------------------------------------------------------
    // PRODUCTOS Y CATEGORÍAS
    // ----------------------------------------------------------
    @GetMapping("/productos")
    public List<Producto> getProductos() {
        return productoRepository.findAll();
    }

    @GetMapping("/categorias")
    public List<Categoria> getCategorias() {
        return categoriaRepository.findByActivoTrueOrderByOrdenMenuAsc();
    }

    @PutMapping("/productos/{id}/stock")
    public ResponseEntity<?> adjustStock(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        int delta = body.getOrDefault("delta", 0);
        return productoRepository.findById(id).map(prod -> {
            BigDecimal nuevoStock = prod.getStockActual().add(BigDecimal.valueOf(delta));
            if (nuevoStock.compareTo(BigDecimal.ZERO) < 0) {
                nuevoStock = BigDecimal.ZERO;
            }
            prod.setStockActual(nuevoStock);
            prod.setDisponible(nuevoStock.compareTo(BigDecimal.ZERO) > 0);
            productoRepository.save(prod);
            return ResponseEntity.ok(prod);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ----------------------------------------------------------
    // PEDIDOS Y COMANDAS
    // ----------------------------------------------------------
    public static class PedidoRequest {
        public String numeroMesa;
        public String observaciones;
        public String origen = "SALON";
        public List<ItemRequest> items;

        public static class ItemRequest {
            public Long idProducto;
            public BigDecimal cantidad;
            public String notas;
        }
    }

    @PostMapping("/pedidos")
    @Transactional
    public ResponseEntity<?> crearPedido(@RequestBody PedidoRequest req) {
        Mesa mesa = mesaRepository.findByNumeroMesa(req.numeroMesa).orElse(null);
        if (mesa == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mesa no encontrada: " + req.numeroMesa));
        }

        Usuario mesero = usuarioRepository.findAll().stream().findFirst().orElse(null);

        Pedido pedido = new Pedido();
        pedido.setMesa(mesa);
        pedido.setUsuarioMesero(mesero);
        pedido.setOrigen(req.origen != null ? req.origen : "SALON");
        pedido.setEstadoPedido("PENDIENTE");
        pedido.setObservaciones(req.observaciones);

        BigDecimal total = BigDecimal.ZERO;

        for (PedidoRequest.ItemRequest item : req.items) {
            Producto prod = productoRepository.findById(item.idProducto).orElse(null);
            if (prod == null) continue;

            // Validar y descontar stock
            if (prod.getControlaStock()) {
                if (prod.getStockActual().compareTo(item.cantidad) < 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Stock insuficiente para: " + prod.getNombreProducto()));
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

        // Actualizar estado de la mesa a OCUPADA
        mesa.setEstado("OCUPADA");
        if (mesa.getFechaApertura() == null) {
            mesa.setFechaApertura(LocalDateTime.now());
        }
        if (mesa.getComensalesActuales() == 0) {
            mesa.setComensalesActuales(2);
        }
        mesaRepository.save(mesa);

        return ResponseEntity.ok(Map.of(
            "idPedido", pedido.getIdPedido(),
            "total", total,
            "estado", pedido.getEstadoPedido(),
            "mensaje", "Pedido enviado a cocina con éxito"
        ));
    }

    // ----------------------------------------------------------
    // COCINA / KDS
    // ----------------------------------------------------------
    @GetMapping("/cocina/tickets")
    public List<Map<String, Object>> getTicketsCocina() {
        List<Pedido> pedidosActivos = pedidoRepository.findByEstadoPedido("PENDIENTE");
        pedidosActivos.addAll(pedidoRepository.findByEstadoPedido("EN_PREPARACION"));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Pedido p : pedidosActivos) {
            List<String> itemsText = new ArrayList<>();
            for (DetallePedido d : p.getDetalles()) {
                itemsText.add(d.getCantidad().intValue() + " × " + d.getProducto().getNombreProducto());
            }

            Map<String, Object> map = new HashMap<>();
            map.put("idPedido", p.getIdPedido());
            map.put("table", p.getMesa() != null ? p.getMesa().getNumeroMesa() : "Barra");
            map.put("state", "PENDIENTE".equalsIgnoreCase(p.getEstadoPedido()) ? "Pendiente" : "Preparando");
            map.put("items", itemsText);
            map.put("note", p.getObservaciones());
            map.put("total", p.getTotalCalculado());
            result.add(map);
        }
        return result;
    }

    @PutMapping("/cocina/pedidos/{id}/avanzar")
    @Transactional
    public ResponseEntity<?> avanzarPedidoCocina(@PathVariable Long id) {
        return pedidoRepository.findById(id).map(p -> {
            if ("PENDIENTE".equalsIgnoreCase(p.getEstadoPedido())) {
                p.setEstadoPedido("EN_PREPARACION");
                for (DetallePedido d : p.getDetalles()) {
                    d.setEstadoCocina("EN_PREPARACION");
                }
            } else if ("EN_PREPARACION".equalsIgnoreCase(p.getEstadoPedido())) {
                p.setEstadoPedido("LISTO");
                for (DetallePedido d : p.getDetalles()) {
                    d.setEstadoCocina("LISTO");
                }
            } else {
                p.setEstadoPedido("ENTREGADO");
                for (DetallePedido d : p.getDetalles()) {
                    d.setEstadoCocina("ENTREGADO");
                }
            }
            pedidoRepository.save(p);
            return ResponseEntity.ok(Map.of("idPedido", p.getIdPedido(), "nuevoEstado", p.getEstadoPedido()));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ----------------------------------------------------------
    // CAJA Y COBROS
    // ----------------------------------------------------------
    public static class CobroRequest {
        public String numeroMesa;
        public String tipoComprobante; // BOLETA, FACTURA
        public String metodoPago;      // EFECTIVO, TARJETA, YAPE, PLIN
        public BigDecimal montoRecibido;
    }

    @PostMapping("/caja/cobrar")
    @Transactional
    public ResponseEntity<?> procesarCobro(@RequestBody CobroRequest req) {
        Mesa mesa = mesaRepository.findByNumeroMesa(req.numeroMesa).orElse(null);
        if (mesa == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mesa no encontrada"));
        }

        // Buscar pedido activo de la mesa
        List<String> estadosValidos = List.of("PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO");
        Pedido pedido = pedidoRepository.findFirstByMesaIdMesaAndEstadoPedidoInOrderByFechaHoraDesc(mesa.getIdMesa(), estadosValidos)
                .orElse(null);

        if (pedido == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No hay pedido activo para cobrar en la mesa " + req.numeroMesa));
        }

        SesionCaja sesion = sesionCajaRepository.findFirstByEstadoOrderByFechaAperturaDesc("ABIERTA")
                .orElse(null);
        if (sesion == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No hay una sesión de caja abierta"));
        }

        MetodoPago metodo = metodoPagoRepository.findByNombreMetodo(req.metodoPago.toUpperCase())
                .orElseGet(() -> metodoPagoRepository.findAll().stream().findFirst().orElse(null));

        BigDecimal total = pedido.getTotalCalculado();
        BigDecimal subtotal = total.divide(BigDecimal.valueOf(1.18), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal);

        BigDecimal recibido = req.montoRecibido != null ? req.montoRecibido : total;
        BigDecimal vuelto = recibido.compareTo(total) >= 0 ? recibido.subtract(total) : BigDecimal.ZERO;

        String serie = "FACTURA".equalsIgnoreCase(req.tipoComprobante) ? "F001" : "B001";
        Integer ultimoNumero = comprobantePagoRepository.findUltimoNumeroPorSerie(serie, req.tipoComprobante);
        Integer nuevoNumero = ultimoNumero + 1;

        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setPedido(pedido);
        comprobante.setSesionCaja(sesion);
        comprobante.setMetodoPago(metodo);
        comprobante.setTipoComprobante(req.tipoComprobante);
        comprobante.setSerie(serie);
        comprobante.setNumero(nuevoNumero);
        comprobante.setSubtotal(subtotal);
        comprobante.setIgv(igv);
        comprobante.setImporteTotal(total);
        comprobante.setMontoRecibido(recibido);
        comprobante.setVueltoEntregado(vuelto);
        comprobantePagoRepository.save(comprobante);

        // Actualizar sesión de caja
        sesion.setTotalVentas(sesion.getTotalVentas().add(total));
        sesionCajaRepository.save(sesion);

        // Liberar mesa a estado LIMPIEZA
        mesa.setEstado("LIMPIEZA");
        mesa.setComensalesActuales(0);
        mesa.setFechaApertura(null);
        mesaRepository.save(mesa);

        return ResponseEntity.ok(Map.of(
            "comprobante", serie + "-" + String.format("%06d", nuevoNumero),
            "total", total,
            "vuelto", vuelto,
            "mensaje", "Cobro registrado exitosamente. Mesa en estado Limpieza."
        ));
    }

    @GetMapping("/caja/resumen")
    public Map<String, Object> getResumenCaja() {
        SesionCaja sesion = sesionCajaRepository.findFirstByEstadoOrderByFechaAperturaDesc("ABIERTA")
                .orElse(null);

        BigDecimal totalVentas = sesion != null ? sesion.getTotalVentas() : BigDecimal.ZERO;
        List<ComprobantePago> comprobantes = sesion != null
                ? comprobantePagoRepository.findBySesionCajaIdSesion(sesion.getIdSesion())
                : List.of();

        BigDecimal efectivo = BigDecimal.ZERO;
        BigDecimal tarjeta = BigDecimal.ZERO;
        BigDecimal billeteras = BigDecimal.ZERO;

        for (ComprobantePago c : comprobantes) {
            String m = c.getMetodoPago().getNombreMetodo().toUpperCase();
            if ("EFECTIVO".equals(m)) efectivo = efectivo.add(c.getImporteTotal());
            else if ("TARJETA".equals(m)) tarjeta = tarjeta.add(c.getImporteTotal());
            else billeteras = billeteras.add(c.getImporteTotal());
        }

        Map<String, Object> res = new HashMap<>();
        res.put("totalRecaudado", totalVentas);
        res.put("transacciones", comprobantes.size());
        res.put("efectivo", efectivo);
        res.put("tarjeta", tarjeta);
        res.put("billeteras", billeteras);
        res.put("montoInicial", sesion != null ? sesion.getMontoInicial() : BigDecimal.ZERO);
        return res;
    }
}
