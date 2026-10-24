package com.restogestion.service;

import com.restogestion.entity.ComprobantePago;
import com.restogestion.entity.Mesa;
import com.restogestion.entity.MetodoPago;
import com.restogestion.entity.Pedido;
import com.restogestion.entity.SesionCaja;
import com.restogestion.repository.ComprobantePagoRepository;
import com.restogestion.repository.MesaRepository;
import com.restogestion.repository.MetodoPagoRepository;
import com.restogestion.repository.PedidoRepository;
import com.restogestion.repository.SesionCajaRepository;
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

    public CajaService(SesionCajaRepository sesionCajaRepository,
                       ComprobantePagoRepository comprobantePagoRepository,
                       PedidoRepository pedidoRepository,
                       MesaRepository mesaRepository,
                       MetodoPagoRepository metodoPagoRepository) {
        this.sesionCajaRepository = sesionCajaRepository;
        this.comprobantePagoRepository = comprobantePagoRepository;
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.metodoPagoRepository = metodoPagoRepository;
    }

    public Optional<SesionCaja> obtenerSesionActiva() {
        return sesionCajaRepository.findFirstByEstadoOrderByFechaAperturaDesc("ABIERTA");
    }

    @Transactional
    public ComprobantePago registrarCobro(String numeroMesa, String tipoComprobante, String metodoNombre, BigDecimal montoRecibido) {
        Mesa mesa = mesaRepository.findByNumeroMesa(numeroMesa)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + numeroMesa));

        List<String> estadosValidos = List.of("PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO");
        Pedido pedido = pedidoRepository.findFirstByMesaIdMesaAndEstadoPedidoInOrderByFechaHoraDesc(mesa.getIdMesa(), estadosValidos)
                .orElseThrow(() -> new IllegalStateException("No existe pedido pendiente de cobro para la mesa " + numeroMesa));

        SesionCaja sesion = obtenerSesionActiva()
                .orElseThrow(() -> new IllegalStateException("No existe una sesión de caja ABIERTA actualmente."));

        MetodoPago metodo = metodoPagoRepository.findByNombreMetodo(metodoNombre.toUpperCase())
                .orElseGet(() -> metodoPagoRepository.findAll().stream().findFirst().orElseThrow());

        BigDecimal total = pedido.getTotalCalculado();
        BigDecimal subtotal = total.divide(BigDecimal.valueOf(1.18), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal);

        BigDecimal recibido = (montoRecibido != null && montoRecibido.compareTo(total) >= 0) ? montoRecibido : total;
        BigDecimal vuelto = recibido.subtract(total);

        String serie = "FACTURA".equalsIgnoreCase(tipoComprobante) ? "F001" : "B001";
        Integer ultimoNumero = comprobantePagoRepository.findUltimoNumeroPorSerie(serie, tipoComprobante);
        Integer nuevoNumero = ultimoNumero + 1;

        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setPedido(pedido);
        comprobante.setSesionCaja(sesion);
        comprobante.setMetodoPago(metodo);
        comprobante.setTipoComprobante(tipoComprobante);
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
            sesion.setFechaCierre(LocalDateTime.now());
            sesion.setSaldoFinalFisico(saldoFinalFisico);
            sesion.setEstado("CERRADA");

            // Calcular ventas en efectivo
            List<ComprobantePago> comprobantes = comprobantePagoRepository.findBySesionCajaIdSesion(sesion.getIdSesion());
            BigDecimal efectivo = comprobantes.stream()
                    .filter(c -> "EFECTIVO".equalsIgnoreCase(c.getMetodoPago().getNombreMetodo()))
                    .map(ComprobantePago::getImporteTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal esperado = sesion.getMontoInicial().add(efectivo);
            BigDecimal descuadre = saldoFinalFisico.subtract(esperado);
            sesion.setDescuadre(descuadre);

            return sesionCajaRepository.save(sesion);
        }).orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada"));
    }
}
