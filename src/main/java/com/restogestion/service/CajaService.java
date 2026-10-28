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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CajaService {

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

    public Optional<SesionCaja> obtenerSesionActiva() {
        return sesionCajaRepository.findFirstByEstadoOrderByFechaAperturaDesc("ABIERTA");
    }

    public BigDecimal calcularSaldoEsperado(SesionCaja sesion) {
        List<ComprobantePago> comprobantes = comprobantePagoRepository.findBySesionCajaIdSesion(sesion.getIdSesion());
        BigDecimal efectivo = comprobantes.stream()
                .filter(c -> "EFECTIVO".equalsIgnoreCase(c.getMetodoPago().getNombreMetodo()))
                .map(ComprobantePago::getImporteTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sesion.getMontoInicial().add(efectivo);
    }

    @Transactional
    public SesionCaja abrirCaja(BigDecimal montoInicial, String usernameCajero) {
        if (montoInicial == null || montoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El monto inicial no puede ser negativo");
        }
        if (obtenerSesionActiva().isPresent()) {
            throw new IllegalStateException("Ya existe una sesión de caja abierta");
        }
        Usuario cajero = usuarioRepository.findByUsername(usernameCajero)
                .filter(usuario -> "ACTIVO".equalsIgnoreCase(usuario.getEstado()))
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado no está disponible"));
        SesionCaja sesion = new SesionCaja();
        sesion.setUsuarioCajero(cajero);
        sesion.setMontoInicial(montoInicial);
        sesion.setTotalVentas(BigDecimal.ZERO);
        sesion.setEstado("ABIERTA");
        return sesionCajaRepository.save(sesion);
    }

    @Transactional
    public ComprobantePago registrarCobro(String numeroMesa, String tipoComprobante, String metodoNombre, BigDecimal montoRecibido) {
        if (numeroMesa == null || numeroMesa.isBlank()) {
            throw new IllegalArgumentException("La mesa es obligatoria");
        }
        if (metodoNombre == null || metodoNombre.isBlank()) {
            throw new IllegalArgumentException("El método de pago es obligatorio");
        }
        Mesa mesa = mesaRepository.findByNumeroMesa(numeroMesa)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + numeroMesa));

        List<String> estadosValidos = List.of("PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO");
        Pedido pedido = pedidoRepository.findFirstByMesaIdMesaAndEstadoPedidoInOrderByFechaHoraDesc(mesa.getIdMesa(), estadosValidos)
                .orElseThrow(() -> new IllegalStateException("No existe pedido pendiente de cobro para la mesa " + numeroMesa));

        if (comprobantePagoRepository.findByPedidoIdPedido(pedido.getIdPedido()).isPresent()) {
            throw new IllegalStateException("El pedido ya tiene un comprobante registrado");
        }

        SesionCaja sesion = obtenerSesionActiva()
                .orElseThrow(() -> new IllegalStateException("No existe una sesión de caja ABIERTA actualmente."));

        MetodoPago metodo = metodoPagoRepository.findByNombreMetodo(metodoNombre.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Método de pago no válido: " + metodoNombre));

        BigDecimal total = pedido.getTotalCalculado();
        BigDecimal subtotal = total.divide(BigDecimal.valueOf(1.18), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal);

        BigDecimal recibido = montoRecibido != null ? montoRecibido : total;
        if (recibido.compareTo(total) < 0) {
            throw new IllegalArgumentException("El monto recibido es menor al total del pedido");
        }
        BigDecimal vuelto = recibido.subtract(total);

        String tipo = "FACTURA".equalsIgnoreCase(tipoComprobante) ? "FACTURA" : "BOLETA";
        String serie = "FACTURA".equals(tipo) ? "F001" : "B001";
        Integer ultimoNumero = comprobantePagoRepository.findUltimoNumeroPorSerie(serie, tipo);
        Integer nuevoNumero = ultimoNumero + 1;

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
        comprobantePagoRepository.save(comprobante);

        // Acumular ventas en la sesión de caja
        sesion.setTotalVentas(sesion.getTotalVentas().add(total));
        sesionCajaRepository.save(sesion);

        // Liberar mesa a estado LIMPIEZA
        mesa.setEstado("LIMPIEZA");
        mesa.setComensalesActuales(0);
        mesa.setFechaApertura(null);
        mesaRepository.save(mesa);

        return comprobante;
    }

    @Transactional
    public SesionCaja cerrarCaja(Long idSesion, BigDecimal saldoFinalFisico) {
        return sesionCajaRepository.findById(idSesion).map(sesion -> {
            if (!"ABIERTA".equalsIgnoreCase(sesion.getEstado())) {
                throw new IllegalStateException("La sesión de caja ya está cerrada");
            }
            BigDecimal esperado = calcularSaldoEsperado(sesion);
            BigDecimal saldoCierre = saldoFinalFisico == null ? esperado : saldoFinalFisico;
            if (saldoCierre.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El saldo físico final no puede ser negativo");
            }
            sesion.setFechaCierre(LocalDateTime.now());
            sesion.setSaldoFinalFisico(saldoFinalFisico);
            sesion.setEstado("CERRADA");

            BigDecimal descuadre = saldoCierre.subtract(esperado);
            sesion.setDescuadre(descuadre);

            return sesionCajaRepository.save(sesion);
        }).orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada"));
    }
}
