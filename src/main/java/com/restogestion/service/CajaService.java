package com.restogestion.service;

import com.restogestion.entity.ComprobantePago;
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
import java.util.List;
import java.util.Optional;

/**
 * Módulo de Facturación, Cobro y Caja.
 * RF-04: apertura de caja (sesión de caja).
 * RF-11: cobro de la cuenta y emisión de boleta/factura con IGV.
 * RF-12: arqueo y cierre de caja con cálculo de descuadre.
 */
@Service
public class CajaService {

    private static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");
    private static final List<String> ESTADOS_COBRABLES = List.of("PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO");

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
    public record MesaPorCobrar(Mesa mesa, Pedido pedido) {}

    public record CuentaCobro(Mesa mesa, Pedido pedido, BigDecimal subtotal, BigDecimal igv, BigDecimal total) {}

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
    @Transactional(readOnly = true)
    public List<MesaPorCobrar> listarMesasPorCobrar() {
        List<MesaPorCobrar> lista = new ArrayList<>();
        for (Mesa mesa : mesaRepository.findAllByOrderByNumeroMesaAsc()) {
            if ("CUENTA".equals(mesa.getEstado()) || "OCUPADA".equals(mesa.getEstado())) {
                lista.add(new MesaPorCobrar(mesa, buscarPedidoPorCobrar(mesa).orElse(null)));
            }
        }
        return lista;
    }

    @Transactional(readOnly = true)
    public CuentaCobro obtenerCuenta(String numeroMesa) {
        Mesa mesa = buscarMesa(numeroMesa);
        Pedido pedido = buscarPedidoPorCobrar(mesa)
                .orElseThrow(() -> new IllegalStateException("No existe pedido pendiente de cobro para la mesa " + numeroMesa));
        pedido.getDetalles().size(); // carga los ítems para mostrarlos en la vista
        BigDecimal total = pedido.getTotalCalculado();
        BigDecimal subtotal = calcularSubtotal(total);
        return new CuentaCobro(mesa, pedido, subtotal, total.subtract(subtotal), total);
    }

    @Transactional
    public ComprobantePago registrarCobro(String numeroMesa, String tipoComprobante, String metodoNombre, BigDecimal montoRecibido) {
        String tipo = tipoComprobante == null ? "" : tipoComprobante.trim().toUpperCase();
        if (!tipo.equals("BOLETA") && !tipo.equals("FACTURA")) {
            throw new IllegalArgumentException("El tipo de comprobante debe ser BOLETA o FACTURA.");
        }

        Mesa mesa = buscarMesa(numeroMesa);
        Pedido pedido = buscarPedidoPorCobrar(mesa)
                .orElseThrow(() -> new IllegalStateException("No existe pedido pendiente de cobro para la mesa " + numeroMesa));

        SesionCaja sesion = obtenerSesionActiva()
                .orElseThrow(() -> new IllegalStateException("No existe una sesión de caja ABIERTA. Abre la caja antes de cobrar."));

        String nombreMetodo = metodoNombre == null ? "" : metodoNombre.trim().toUpperCase();
        MetodoPago metodo = metodoPagoRepository.findByNombreMetodo(nombreMetodo)
                .filter(m -> Boolean.TRUE.equals(m.getActivo()))
                .orElseThrow(() -> new IllegalArgumentException("Método de pago no válido o inactivo: " + metodoNombre));

        BigDecimal total = pedido.getTotalCalculado();
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

        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setPedido(pedido);
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

    public List<ComprobantePago> listarComprobantesDeSesion(Long idSesion) {
        List<ComprobantePago> lista = new ArrayList<>(comprobantePagoRepository.findBySesionCajaIdSesion(idSesion));
        lista.sort((a, b) -> b.getFechaEmision().compareTo(a.getFechaEmision()));
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

    /** Último pedido activo de la mesa que aún no tiene comprobante emitido. */
    private Optional<Pedido> buscarPedidoPorCobrar(Mesa mesa) {
        return pedidoRepository
                .findFirstByMesaIdMesaAndEstadoPedidoInOrderByFechaHoraDesc(mesa.getIdMesa(), ESTADOS_COBRABLES)
                .filter(p -> comprobantePagoRepository.findByPedidoIdPedido(p.getIdPedido()).isEmpty());
    }

    private BigDecimal calcularSubtotal(BigDecimal total) {
        return total.divide(FACTOR_IGV, 2, RoundingMode.HALF_UP);
    }
}
