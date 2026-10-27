package com.restogestion.service;

import com.restogestion.entity.ComprobantePago;
import com.restogestion.entity.DetallePedido;
import com.restogestion.entity.Mesa;
import com.restogestion.entity.MetodoPago;
import com.restogestion.entity.Pedido;
import com.restogestion.entity.SesionCaja;
import com.restogestion.entity.Usuario;
import com.restogestion.repository.ComprobantePagoRepository;
import com.restogestion.repository.MesaRepository;
import com.restogestion.repository.MetodoPagoRepository;
import com.restogestion.repository.PedidoRepository;
import com.restogestion.repository.SesionCajaRepository;
import com.restogestion.repository.UsuarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Módulo de Facturación, Cobro y Caja.
 * RF-04: apertura de caja (sesión de caja).
 * RF-11: cobro de la cuenta de una mesa y emisión de boleta/factura con IGV.
 * RF-12: arqueo y cierre de caja con cálculo de descuadre.
 *
 * Una mesa puede tener varios pedidos (cada envío a cocina crea uno). La cuenta de la mesa
 * es la suma de todos los pedidos posteriores al último pedido ya cobrado.
 */
@Service
public class CajaService {

    private static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("1000000");

    private final SesionCajaRepository sesionCajaRepository;
    private final ComprobantePagoRepository comprobantePagoRepository;
    private final PedidoRepository pedidoRepository;
    private final MesaRepository mesaRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final UsuarioRepository usuarioRepository;

    public CajaService(SesionCajaRepository sesionCajaRepository,
                       ComprobantePagoRepository comprobantePagoRepository,
                       PedidoRepository pedidoRepository,
                       MesaRepository mesaRepository,
                       MetodoPagoRepository metodoPagoRepository,
                       UsuarioRepository usuarioRepository) {
        this.sesionCajaRepository = sesionCajaRepository;
        this.comprobantePagoRepository = comprobantePagoRepository;
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.metodoPagoRepository = metodoPagoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ----------------------------------------------------------
    // Objetos de apoyo para las vistas
    // ----------------------------------------------------------
    public record ItemCuenta(String producto, BigDecimal cantidad, BigDecimal precioUnitario,
                             BigDecimal subtotal, String observaciones) {}

    public record MesaPorCobrar(Mesa mesa, int pedidos, BigDecimal total) {}

    public record CuentaCobro(Mesa mesa, List<Pedido> pedidos, List<ItemCuenta> items,
                              BigDecimal subtotal, BigDecimal igv, BigDecimal total) {}

    public record ResumenCaja(BigDecimal montoInicial, BigDecimal totalVentas, BigDecimal efectivo,
                              BigDecimal tarjeta, BigDecimal yape, BigDecimal plin,
                              int transacciones, BigDecimal efectivoEsperado) {}

    // ----------------------------------------------------------
    // RF-04: Apertura de caja
    // ----------------------------------------------------------
    public Optional<SesionCaja> obtenerSesionActiva() {
        return sesionCajaRepository.findFirstByEstadoOrderByFechaAperturaDesc("ABIERTA");
    }

    @Transactional
    public SesionCaja abrirCaja(BigDecimal montoInicial) {
        if (montoInicial == null || montoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El monto inicial debe ser un número mayor o igual a 0.");
        }
        if (montoInicial.compareTo(MONTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("El monto inicial ingresado no es válido.");
        }
        if (obtenerSesionActiva().isPresent()) {
            throw new IllegalStateException("Ya existe una sesión de caja abierta. Ciérrala antes de abrir otra.");
        }
        Usuario cajero = usuarioRepository.findByUsername("cajero")
                .or(() -> usuarioRepository.findAll().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No hay usuarios registrados para asignar como cajero."));

        SesionCaja sesion = new SesionCaja();
        sesion.setUsuarioCajero(cajero);
        sesion.setMontoInicial(montoInicial.setScale(2, RoundingMode.HALF_UP));
        sesion.setTotalVentas(BigDecimal.ZERO);
        sesion.setEstado("ABIERTA");
        return sesionCajaRepository.save(sesion);
    }

    // ----------------------------------------------------------
    // RF-11: Cobro y comprobantes
    // ----------------------------------------------------------
    public List<MetodoPago> listarMetodosPago() {
        return metodoPagoRepository.findByActivoTrue();
    }

    @Transactional(readOnly = true)
    public List<MesaPorCobrar> listarMesasPorCobrar() {
        List<MesaPorCobrar> lista = new ArrayList<>();
        for (Mesa mesa : mesaRepository.findAllByOrderByNumeroMesaAsc()) {
            if ("CUENTA".equals(mesa.getEstado()) || "OCUPADA".equals(mesa.getEstado())) {
                List<Pedido> pedidos = pedidosPorCobrar(mesa);
                lista.add(new MesaPorCobrar(mesa, pedidos.size(), sumarTotal(pedidos)));
            }
        }
        return lista;
    }

    @Transactional(readOnly = true)
    public int contarMesasPendientesDeCobro() {
        return (int) listarMesasPorCobrar().stream().filter(m -> m.pedidos() > 0).count();
    }

    @Transactional(readOnly = true)
    public CuentaCobro obtenerCuenta(String numeroMesa) {
        Mesa mesa = buscarMesa(numeroMesa);
        List<Pedido> pedidos = pedidosPorCobrar(mesa);
        if (pedidos.isEmpty()) {
            throw new IllegalStateException("No existe pedido pendiente de cobro para la mesa " + numeroMesa);
        }
        BigDecimal total = sumarTotal(pedidos);
        BigDecimal subtotal = calcularSubtotal(total);
        return new CuentaCobro(mesa, pedidos, aItems(pedidos), subtotal, total.subtract(subtotal), total);
    }

    @Transactional
    public ComprobantePago registrarCobro(String numeroMesa, String tipoComprobante, String metodoNombre, BigDecimal montoRecibido) {
        String tipo = tipoComprobante == null ? "" : tipoComprobante.trim().toUpperCase();
        if (!tipo.equals("BOLETA") && !tipo.equals("FACTURA")) {
            throw new IllegalArgumentException("El tipo de comprobante debe ser BOLETA o FACTURA.");
        }

        Mesa mesa = buscarMesa(numeroMesa);
        List<Pedido> pedidos = pedidosPorCobrar(mesa);
        if (pedidos.isEmpty()) {
            throw new IllegalStateException("No existe pedido pendiente de cobro para la mesa " + numeroMesa);
        }

        SesionCaja sesion = obtenerSesionActiva()
                .orElseThrow(() -> new IllegalStateException("No existe una sesión de caja ABIERTA. Abre la caja antes de cobrar."));

        String nombreMetodo = metodoNombre == null ? "" : metodoNombre.trim().toUpperCase();
        MetodoPago metodo = metodoPagoRepository.findByNombreMetodo(nombreMetodo)
                .filter(m -> Boolean.TRUE.equals(m.getActivo()))
                .orElseThrow(() -> new IllegalArgumentException("Método de pago no válido o inactivo: " + metodoNombre));

        BigDecimal total = sumarTotal(pedidos);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("La cuenta de la mesa " + numeroMesa + " no tiene importe por cobrar.");
        }
        BigDecimal subtotal = calcularSubtotal(total);
        BigDecimal igv = total.subtract(subtotal);

        BigDecimal recibido;
        if (Boolean.TRUE.equals(metodo.getRequiereVuelto())) {
            if (montoRecibido == null || montoRecibido.compareTo(total) < 0) {
                throw new IllegalArgumentException("El monto recibido es insuficiente. Total a cobrar: S/ " + total);
            }
            recibido = montoRecibido.setScale(2, RoundingMode.HALF_UP);
        } else {
            recibido = total;
        }
        BigDecimal vuelto = recibido.subtract(total);

        String serie = tipo.equals("FACTURA") ? "F001" : "B001";
        int nuevoNumero = comprobantePagoRepository.findUltimoNumeroPorSerie(serie, tipo) + 1;

        // El comprobante se vincula al último pedido de la cuenta; el importe es el de toda la mesa.
        Pedido pedidoReferencia = pedidos.get(pedidos.size() - 1);

        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setPedido(pedidoReferencia);
        comprobante.setSesionCaja(sesion);
        comprobante.setMetodoPago(metodo);
        comprobante.setTipoComprobante(tipo);
        comprobante.setSerie(serie);
        comprobante.setNumero(nuevoNumero);
        comprobante.setSubtotal(subtotal);
        comprobante.setIgv(igv);
        comprobante.setImporteTotal(total);
        comprobante.setMontoRecibido(recibido);
        comprobante.setVueltoEntregado(vuelto);
        comprobante = comprobantePagoRepository.save(comprobante);

        sesion.setTotalVentas(sesion.getTotalVentas().add(total));
        sesionCajaRepository.save(sesion);

        mesa.setEstado("LIMPIEZA");
        mesa.setComensalesActuales(0);
        mesa.setFechaApertura(null);
        mesaRepository.save(mesa);

        return comprobante;
    }

    public Optional<ComprobantePago> obtenerComprobante(Long idComprobante) {
        return comprobantePagoRepository.findById(idComprobante);
    }

    /** Ítems consumidos que cubre un comprobante (todos los pedidos de la cuenta cobrada). */
    @Transactional(readOnly = true)
    public List<ItemCuenta> obtenerItemsComprobante(ComprobantePago comprobante) {
        Pedido referencia = comprobante.getPedido();
        if (referencia.getMesa() == null) {
            return aItems(List.of(referencia));
        }
        List<Pedido> todos = pedidosActivosDeMesa(referencia.getMesa());
        return aItems(agruparCuenta(todos, referencia.getFechaHora()));
    }

    public List<ComprobantePago> listarComprobantesDeSesion(Long idSesion) {
        List<ComprobantePago> lista = new ArrayList<>(comprobantePagoRepository.findBySesionCajaIdSesion(idSesion));
        lista.sort(Comparator.comparing(ComprobantePago::getFechaEmision).reversed());
        return lista;
    }

    // ----------------------------------------------------------
    // RF-12: Arqueo y cierre de caja
    // ----------------------------------------------------------
    public ResumenCaja resumirSesion(SesionCaja sesion) {
        List<ComprobantePago> comprobantes = comprobantePagoRepository.findBySesionCajaIdSesion(sesion.getIdSesion());
        BigDecimal efectivo = BigDecimal.ZERO;
        BigDecimal tarjeta = BigDecimal.ZERO;
        BigDecimal yape = BigDecimal.ZERO;
        BigDecimal plin = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (ComprobantePago c : comprobantes) {
            BigDecimal importe = c.getImporteTotal();
            total = total.add(importe);
            switch (c.getMetodoPago().getNombreMetodo().toUpperCase()) {
                case "EFECTIVO" -> efectivo = efectivo.add(importe);
                case "TARJETA" -> tarjeta = tarjeta.add(importe);
                case "YAPE" -> yape = yape.add(importe);
                case "PLIN" -> plin = plin.add(importe);
                default -> { }
            }
        }
        BigDecimal esperado = sesion.getMontoInicial().add(efectivo);
        return new ResumenCaja(sesion.getMontoInicial(), total, efectivo, tarjeta, yape, plin,
                comprobantes.size(), esperado);
    }

    @Transactional
    public SesionCaja cerrarCaja(Long idSesion, BigDecimal saldoFinalFisico) {
        if (saldoFinalFisico == null || saldoFinalFisico.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El conteo físico debe ser un número mayor o igual a 0.");
        }
        if (saldoFinalFisico.compareTo(MONTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("El conteo físico ingresado no es válido.");
        }
        SesionCaja sesion = sesionCajaRepository.findById(idSesion)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de caja no encontrada."));
        if (!"ABIERTA".equals(sesion.getEstado())) {
            throw new IllegalStateException("La sesión de caja ya fue cerrada.");
        }

        ResumenCaja resumen = resumirSesion(sesion);
        BigDecimal fisico = saldoFinalFisico.setScale(2, RoundingMode.HALF_UP);

        sesion.setFechaCierre(LocalDateTime.now());
        sesion.setSaldoFinalFisico(fisico);
        sesion.setTotalVentas(resumen.totalVentas());
        sesion.setDescuadre(fisico.subtract(resumen.efectivoEsperado()));
        sesion.setEstado("CERRADA");
        return sesionCajaRepository.save(sesion);
    }

    public List<SesionCaja> listarSesiones() {
        return sesionCajaRepository.findAll(Sort.by(Sort.Direction.DESC, "fechaApertura"));
    }

    // ----------------------------------------------------------
    // Utilidades internas
    // ----------------------------------------------------------
    private Mesa buscarMesa(String numeroMesa) {
        return mesaRepository.findByNumeroMesa(numeroMesa)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + numeroMesa));
    }

    /** Pedidos no cancelados de la mesa, del más antiguo al más reciente. */
    private List<Pedido> pedidosActivosDeMesa(Mesa mesa) {
        List<Pedido> pedidos = new ArrayList<>(
                pedidoRepository.findByMesaIdMesaAndEstadoPedidoNot(mesa.getIdMesa(), "CANCELADO"));
        pedidos.sort(Comparator.comparing(Pedido::getFechaHora));
        return pedidos;
    }

    private List<Pedido> pedidosPorCobrar(Mesa mesa) {
        return agruparCuenta(pedidosActivosDeMesa(mesa), null);
    }

    /**
     * Agrupa los pedidos de una misma cuenta: los posteriores al último pedido cobrado
     * (el comprobante se guarda en el último pedido de cada cuenta).
     * Si limiteSuperior no es nulo, solo considera los pedidos hasta esa fecha (cuenta ya cobrada).
     */
    private List<Pedido> agruparCuenta(List<Pedido> todos, LocalDateTime limiteSuperior) {
        LocalDateTime ultimoCobrado = null;
        for (Pedido p : todos) {
            boolean dentro = limiteSuperior == null || p.getFechaHora().isBefore(limiteSuperior);
            if (dentro && comprobantePagoRepository.findByPedidoIdPedido(p.getIdPedido()).isPresent()
                    && (ultimoCobrado == null || p.getFechaHora().isAfter(ultimoCobrado))) {
                ultimoCobrado = p.getFechaHora();
            }
        }
        List<Pedido> cuenta = new ArrayList<>();
        for (Pedido p : todos) {
            boolean hastaLimite = limiteSuperior == null || !p.getFechaHora().isAfter(limiteSuperior);
            boolean despuesDeCobro = ultimoCobrado == null || p.getFechaHora().isAfter(ultimoCobrado);
            if (hastaLimite && despuesDeCobro) {
                cuenta.add(p);
            }
        }
        return cuenta;
    }

    private BigDecimal sumarTotal(List<Pedido> pedidos) {
        BigDecimal total = BigDecimal.ZERO;
        for (Pedido p : pedidos) {
            total = total.add(p.getTotalCalculado());
        }
        return total;
    }

    private List<ItemCuenta> aItems(List<Pedido> pedidos) {
        List<ItemCuenta> items = new ArrayList<>();
        for (Pedido p : pedidos) {
            for (DetallePedido d : p.getDetalles()) {
                items.add(new ItemCuenta(d.getProducto().getNombreProducto(), d.getCantidad(),
                        d.getPrecioUnitario(), d.getSubtotal(), d.getObservaciones()));
            }
        }
        return items;
    }

    private BigDecimal calcularSubtotal(BigDecimal total) {
        return total.divide(FACTOR_IGV, 2, RoundingMode.HALF_UP);
    }
}
