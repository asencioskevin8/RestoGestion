package com.restogestion.controller;

import com.restogestion.entity.*;
import com.restogestion.repository.*;
import com.restogestion.service.CajaService;
import com.restogestion.service.MesaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final MesaService mesaService;
    private final CajaService cajaService;
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final PedidoRepository pedidoRepository;
    private final SesionCajaRepository sesionCajaRepository;
    private final ComprobantePagoRepository comprobantePagoRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final UsuarioRepository usuarioRepository;

    public ApiController(MesaRepository mesaRepository,
                         MesaService mesaService,
                         CajaService cajaService,
                         ProductoRepository productoRepository,
                         CategoriaRepository categoriaRepository,
                         PedidoRepository pedidoRepository,
                         SesionCajaRepository sesionCajaRepository,
                         ComprobantePagoRepository comprobantePagoRepository,
                         MetodoPagoRepository metodoPagoRepository,
                         UsuarioRepository usuarioRepository) {
        this.mesaRepository = mesaRepository;
        this.mesaService = mesaService;
        this.cajaService = cajaService;
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.pedidoRepository = pedidoRepository;
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
        if (mesaService.obtenerPorId(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(mesaService.transicionarEstado(id, body.get("estado")));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
        if (req == null || req.numeroMesa == null || req.numeroMesa.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "La mesa es obligatoria"));
        }
        if (req.items == null || req.items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "La comanda debe contener al menos un producto"));
        }
        Mesa mesa = mesaRepository.findByNumeroMesa(req.numeroMesa).orElse(null);
        if (mesa == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mesa no encontrada: " + req.numeroMesa));
        }

        Usuario mesero = usuarioActual();

        Pedido pedido = new Pedido();
        pedido.setMesa(mesa);
        pedido.setUsuarioMesero(mesero);
        pedido.setOrigen(req.origen != null ? req.origen : "SALON");
        pedido.setEstadoPedido("PENDIENTE");
        pedido.setObservaciones(req.observaciones);

        BigDecimal total = BigDecimal.ZERO;

        for (PedidoRequest.ItemRequest item : req.items) {
            if (item == null || item.idProducto == null || item.cantidad == null
                    || item.cantidad.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cada producto debe tener una cantidad mayor a cero"));
            }
            Producto prod = productoRepository.findById(item.idProducto).orElse(null);
            if (prod == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Producto no encontrado: " + item.idProducto));
            }

            if (!Boolean.TRUE.equals(prod.getDisponible())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Producto no disponible: " + prod.getNombreProducto()));
            }

            // Validar y descontar stock
            if (Boolean.TRUE.equals(prod.getControlaStock())) {
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
        public String tipoComprobante; // BOLETA_SIMPLE, BOLETA_DNI, FACTURA
        public String metodoPago;      // EFECTIVO, TARJETA, YAPE, PLIN
        public String tipoDocumentoCliente;
        public String numeroDocumentoCliente;
        public String nombreCliente;
        public String direccionCliente;
        public BigDecimal montoRecibido;
    }

    public static class CierreCajaRequest {
        public Long idSesion;
        public BigDecimal saldoFinalFisico;
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
        if (comprobantePagoRepository.findByPedidoIdPedido(pedido.getIdPedido()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El pedido ya tiene un comprobante registrado"));
        }

        SesionCaja sesion = sesionCajaRepository.findFirstByEstadoOrderByFechaAperturaDesc("ABIERTA")
                .orElse(null);
        if (sesion == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No hay una sesión de caja abierta"));
        }

        if (req.metodoPago == null || req.metodoPago.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El método de pago es obligatorio"));
        }
        MetodoPago metodo = metodoPagoRepository.findByNombreMetodo(req.metodoPago.toUpperCase())
                .orElse(null);
        if (metodo == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Método de pago no válido: " + req.metodoPago));
        }

       BigDecimal total = pedido.getTotalCalculado();

        // Factor fiscal estándar aplicable según normativa tributaria vigente (IGV 18%)
        final BigDecimal FACTOR_IGV = BigDecimal.valueOf(1.18);
        BigDecimal subtotal = total.divide(FACTOR_IGV, 2, java.math.RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal);

        BigDecimal recibido = req.montoRecibido != null ? req.montoRecibido : total;
        if (recibido.compareTo(total) < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "El monto recibido es menor al total del pedido"));
        }
        BigDecimal vuelto = recibido.subtract(total);

        String modalidad = req.tipoComprobante == null ? "" : req.tipoComprobante.trim().toUpperCase();
        if (!List.of("BOLETA_SIMPLE", "BOLETA_DNI", "FACTURA").contains(modalidad)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de comprobante no válido"));
        }
        if ("BOLETA_DNI".equals(modalidad) && (req.numeroDocumentoCliente == null || !req.numeroDocumentoCliente.matches("\\d{8}"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "El DNI debe tener 8 dígitos"));
        }
        if ("FACTURA".equals(modalidad) && (req.numeroDocumentoCliente == null || !req.numeroDocumentoCliente.matches("\\d{11}"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "El RUC debe tener 11 dígitos"));
        }
        if ("BOLETA_SIMPLE".equals(modalidad)) {
            req.tipoDocumentoCliente = null;
            req.numeroDocumentoCliente = null;
            req.nombreCliente = "Público en general";
            req.direccionCliente = null;
        }
        String tipoPersistido = "FACTURA".equals(modalidad) ? "FACTURA" : "BOLETA";
        String serie = "FACTURA".equals(tipoPersistido) ? "F001" : "B001";
        Integer ultimoNumero = comprobantePagoRepository.findUltimoNumeroPorSerie(serie, tipoPersistido);
        Integer nuevoNumero = ultimoNumero + 1;

        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setPedido(pedido);
        comprobante.setSesionCaja(sesion);
        comprobante.setMetodoPago(metodo);
        comprobante.setTipoComprobante(tipoPersistido);
        comprobante.setSerie(serie);
        comprobante.setNumero(nuevoNumero);
        comprobante.setSubtotal(subtotal);
        comprobante.setIgv(igv);
        comprobante.setImporteTotal(total);
        comprobante.setMontoRecibido(recibido);
        comprobante.setVueltoEntregado(vuelto);
        comprobante.setTipoDocumentoCliente(req.tipoDocumentoCliente);
        comprobante.setNumeroDocumentoCliente(req.numeroDocumentoCliente);
        comprobante.setNombreCliente(req.nombreCliente);
        comprobante.setDireccionCliente(req.direccionCliente);
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
            "idComprobante", comprobante.getIdComprobante(),
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
            switch (m) {
                case "EFECTIVO" -> efectivo = efectivo.add(c.getImporteTotal());
                case "TARJETA" -> tarjeta = tarjeta.add(c.getImporteTotal());
                default -> billeteras = billeteras.add(c.getImporteTotal());
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("totalRecaudado", totalVentas);
        res.put("transacciones", comprobantes.size());
        res.put("efectivo", efectivo);
        res.put("tarjeta", tarjeta);
        res.put("billeteras", billeteras);
        res.put("montoInicial", sesion != null ? sesion.getMontoInicial() : BigDecimal.ZERO);
        res.put("idSesion", sesion != null ? sesion.getIdSesion() : null);
        return res;
    }

    @GetMapping("/reportes")
    @Transactional(readOnly = true)
    public Map<String, Object> getReportes() {
        List<ComprobantePago> comprobantes = comprobantePagoRepository.findAll();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal efectivo = BigDecimal.ZERO;
        BigDecimal tarjeta = BigDecimal.ZERO;
        BigDecimal billeteras = BigDecimal.ZERO;
        List<Map<String, Object>> cobros = new ArrayList<>();

        for (ComprobantePago comprobante : comprobantes) {
            BigDecimal importe = comprobante.getImporteTotal() == null
                    ? BigDecimal.ZERO : comprobante.getImporteTotal();
            String metodo = comprobante.getMetodoPago() == null
                    ? "SIN MÉTODO" : comprobante.getMetodoPago().getNombreMetodo();

            total = total.add(importe);
            switch (metodo.toUpperCase()) {
                case "EFECTIVO" -> efectivo = efectivo.add(importe);
                case "TARJETA" -> tarjeta = tarjeta.add(importe);
                default -> billeteras = billeteras.add(importe);
            }

            Map<String, Object> fila = new HashMap<>();
            fila.put("fecha", comprobante.getFechaEmision());
            fila.put("comprobante", comprobante.getSerie() + "-" + String.format("%06d", comprobante.getNumero()));
            fila.put("pedido", comprobante.getPedido() == null ? null : comprobante.getPedido().getIdPedido());
            fila.put("metodo", metodo);
            fila.put("total", importe);
            cobros.add(fila);
        }

        cobros.sort(Comparator.comparing(
                fila -> (LocalDateTime) fila.get("fecha"),
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("total", total);
        respuesta.put("efectivo", efectivo);
        respuesta.put("tarjeta", tarjeta);
        respuesta.put("billeteras", billeteras);
        respuesta.put("cobros", cobros);
        return respuesta;
    }

    @PostMapping("/caja/cierre")
    @Transactional
    public ResponseEntity<?> cerrarCaja(@RequestBody CierreCajaRequest req) {
        if (req == null || req.idSesion == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "La sesión de caja es obligatoria"));
        }
        try {
            SesionCaja sesion = cajaService.cerrarCaja(req.idSesion, req.saldoFinalFisico);
            return ResponseEntity.ok(Map.of(
                    "idSesion", sesion.getIdSesion(),
                    "estado", sesion.getEstado(),
                    "descuadre", sesion.getDescuadre()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Usuario usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return usuarioRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));
        }
        throw new IllegalStateException("La operación requiere autenticación");
    }
}
