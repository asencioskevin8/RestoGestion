package com.restogestion.service;

import com.restogestion.entity.Mesa;
import com.restogestion.repository.ComprobantePagoRepository;
import com.restogestion.repository.MesaRepository;
import com.restogestion.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MesaService {

    private final MesaRepository mesaRepository;

    // Integración con Pedidos y Caja (solo lectura): permite saber si la mesa tiene algo por cobrar
    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ComprobantePagoRepository comprobantePagoRepository;

    public MesaService(MesaRepository mesaRepository) {
        this.mesaRepository = mesaRepository;
    }

    public List<Mesa> listarTodas() {
        return mesaRepository.findAllByOrderByNumeroMesaAsc();
    }

    public Optional<Mesa> obtenerPorId(Long id) {
        return mesaRepository.findById(id);
    }

    public Optional<Mesa> obtenerPorNumero(String numeroMesa) {
        return mesaRepository.findByNumeroMesa(numeroMesa);
    }

    @Transactional
    public Mesa cambiarEstado(Long id, String nuevoEstado) {
        return mesaRepository.findById(id).map(mesa -> {
            mesa.setEstado(nuevoEstado);
            if ("LIBRE".equalsIgnoreCase(nuevoEstado)) {
                mesa.setComensalesActuales(0);
                mesa.setFechaApertura(null);
            } else if ("OCUPADA".equalsIgnoreCase(nuevoEstado) && mesa.getFechaApertura() == null) {
                mesa.setFechaApertura(LocalDateTime.now());
            }
            return mesaRepository.save(mesa);
        }).orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada con ID: " + id));
    }

    @Transactional
    public Mesa asignarComensales(Long id, int comensales) {
        return mesaRepository.findById(id).map(mesa -> {
            if (comensales > mesa.getCapacidad()) {
                throw new IllegalArgumentException("El número de comensales supera la capacidad máxima de " + mesa.getCapacidad());
            }
            mesa.setComensalesActuales(comensales);
            return mesaRepository.save(mesa);
        }).orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
    }

    // ==========================================================
    // RF-05 · Salón y Mapa de Mesas (ampliación del módulo)
    // ==========================================================

    /** Estados válidos de una mesa (coinciden con el ENUM de la tabla mesas). */
    public static final List<String> ESTADOS = List.of("LIBRE", "OCUPADA", "RESERVADA", "CUENTA", "LIMPIEZA");

    /** Capacidad máxima permitida por mesa al registrarla o editarla. */
    public static final int CAPACIDAD_MAXIMA = 20;

    /**
     * Transiciones de estado permitidas desde el salón.
     * Abrir una mesa (LIBRE/RESERVADA → OCUPADA) se hace con abrirMesa(), porque exige comensales.
     * El cobro (→ LIMPIEZA) lo realiza el módulo de Caja.
     */
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            "LIBRE", Set.of("RESERVADA"),
            "RESERVADA", Set.of("LIBRE"),
            "OCUPADA", Set.of("CUENTA"),
            "CUENTA", Set.of("OCUPADA"),
            "LIMPIEZA", Set.of("LIBRE")
    );

    /** Indicadores que se muestran en la cabecera del mapa de mesas. */
    public record ResumenSalon(int totalMesas, int capacidadTotal, int comensalesActuales, int ocupacionPorcentaje) {}

    public boolean esEstadoValido(String estado) {
        return estado != null && ESTADOS.contains(estado.toUpperCase());
    }

    /** Lista las mesas de un estado; si el estado es nulo o no válido devuelve todas. */
    public List<Mesa> listarPorEstado(String estado) {
        if (!esEstadoValido(estado)) {
            return listarTodas();
        }
        String filtro = estado.toUpperCase();
        return listarTodas().stream().filter(m -> filtro.equals(m.getEstado())).toList();
    }

    /** Cantidad de mesas por estado, en el orden de ESTADOS (incluye los estados con 0 mesas). */
    public Map<String, Long> contarPorEstado() {
        Map<String, Long> conteo = new LinkedHashMap<>();
        ESTADOS.forEach(e -> conteo.put(e, 0L));
        mesaRepository.findAll().forEach(m -> conteo.merge(m.getEstado(), 1L, Long::sum));
        return conteo;
    }

    public ResumenSalon resumen() {
        List<Mesa> mesas = mesaRepository.findAll();
        int total = mesas.size();
        int capacidad = mesas.stream().mapToInt(Mesa::getCapacidad).sum();
        int comensales = mesas.stream().mapToInt(Mesa::getComensalesActuales).sum();
        long enServicio = mesas.stream()
                .filter(m -> "OCUPADA".equals(m.getEstado()) || "CUENTA".equals(m.getEstado()))
                .count();
        int porcentaje = total == 0 ? 0 : (int) Math.round(enServicio * 100.0 / total);
        return new ResumenSalon(total, capacidad, comensales, porcentaje);
    }

    /**
     * Registra una mesa nueva o actualiza número y capacidad de una existente.
     * El estado y los comensales nunca se toman del formulario.
     */
    @Transactional
    public Mesa guardar(Mesa datos) {
        String numero = datos.getNumeroMesa() == null ? "" : datos.getNumeroMesa().trim().toUpperCase();
        if (datos.getCapacidad() == null || datos.getCapacidad() < 1 || datos.getCapacidad() > CAPACIDAD_MAXIMA) {
            throw new IllegalArgumentException("La capacidad debe estar entre 1 y " + CAPACIDAD_MAXIMA + " comensales");
        }

        Optional<Mesa> existente = mesaRepository.findByNumeroMesa(numero);
        if (existente.isPresent() && !existente.get().getIdMesa().equals(datos.getIdMesa())) {
            throw new IllegalArgumentException("Ya existe una mesa con el número " + numero);
        }

        Mesa mesa;
        if (datos.getIdMesa() == null) {
            mesa = new Mesa(numero, datos.getCapacidad());
        } else {
            mesa = obtenerOExcepcion(datos.getIdMesa());
            if (datos.getCapacidad() < mesa.getComensalesActuales()) {
                throw new IllegalArgumentException("La mesa tiene " + mesa.getComensalesActuales()
                        + " comensales; la capacidad no puede ser menor");
            }
            mesa.setNumeroMesa(numero);
            mesa.setCapacidad(datos.getCapacidad());
        }
        return mesaRepository.save(mesa);
    }

    /** Abre una mesa libre o reservada: pasa a OCUPADA con los comensales indicados. */
    @Transactional
    public Mesa abrirMesa(Long id, int comensales) {
        Mesa mesa = obtenerOExcepcion(id);
        if (!"LIBRE".equals(mesa.getEstado()) && !"RESERVADA".equals(mesa.getEstado())) {
            throw new IllegalStateException("Solo se puede abrir una mesa libre o reservada (estado actual: "
                    + mesa.getEstado() + ")");
        }
        validarComensales(mesa, comensales);
        mesa.setEstado("OCUPADA");
        mesa.setComensalesActuales(comensales);
        mesa.setFechaApertura(LocalDateTime.now());
        return mesaRepository.save(mesa);
    }

    /** Actualiza los comensales de una mesa que está ocupada. */
    @Transactional
    public Mesa actualizarComensales(Long id, int comensales) {
        Mesa mesa = obtenerOExcepcion(id);
        if (!"OCUPADA".equals(mesa.getEstado())) {
            throw new IllegalStateException("Solo se pueden modificar los comensales de una mesa ocupada");
        }
        validarComensales(mesa, comensales);
        mesa.setComensalesActuales(comensales);
        return mesaRepository.save(mesa);
    }

    /** Cambia el estado validando que la transición esté permitida desde el salón. */
    @Transactional
    public Mesa transicionarEstado(Long id, String nuevoEstado) {
        if (!esEstadoValido(nuevoEstado)) {
            throw new IllegalArgumentException("Estado no válido: " + nuevoEstado);
        }
        Mesa mesa = obtenerOExcepcion(id);
        String destino = nuevoEstado.toUpperCase();
        if (!TRANSICIONES.getOrDefault(mesa.getEstado(), Set.of()).contains(destino)) {
            throw new IllegalStateException("No se puede pasar la mesa " + mesa.getNumeroMesa()
                    + " de " + mesa.getEstado() + " a " + destino);
        }
        // Integración con Pedidos/Caja: solo se pide la cuenta si Caja tendrá un pedido que cobrar
        if ("OCUPADA".equals(mesa.getEstado()) && "CUENTA".equals(destino) && !tienePedidoPorCobrar(mesa)) {
            throw new IllegalStateException("La mesa " + mesa.getNumeroMesa()
                    + " no tiene un pedido por cobrar: registra una comanda antes de pedir la cuenta");
        }
        return cambiarEstado(id, destino);
    }

    /** Estados de pedido que Caja considera cobrables (mismo criterio que CajaService). */
    private static final List<String> ESTADOS_PEDIDO_COBRABLES =
            List.of("PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO");

    /** Último pedido activo de la mesa que todavía no tiene comprobante de pago. */
    private boolean tienePedidoPorCobrar(Mesa mesa) {
        return pedidoRepository
                .findFirstByMesaIdMesaAndEstadoPedidoInOrderByFechaHoraDesc(mesa.getIdMesa(), ESTADOS_PEDIDO_COBRABLES)
                .map(p -> comprobantePagoRepository.findByPedidoIdPedido(p.getIdPedido()).isEmpty())
                .orElse(false);
    }

    /** Elimina una mesa libre que no tenga pedidos asociados. */
    @Transactional
    public void eliminar(Long id) {
        Mesa mesa = obtenerOExcepcion(id);
        if (!"LIBRE".equals(mesa.getEstado())) {
            throw new IllegalStateException("Solo se pueden eliminar mesas libres");
        }
        try {
            mesaRepository.delete(mesa);
            mesaRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("La mesa " + mesa.getNumeroMesa()
                    + " tiene pedidos registrados y no se puede eliminar");
        }
    }

    private Mesa obtenerOExcepcion(Long id) {
        return mesaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada con ID: " + id));
    }

    private void validarComensales(Mesa mesa, int comensales) {
        if (comensales < 1) {
            throw new IllegalArgumentException("Debe haber al menos 1 comensal");
        }
        if (comensales > mesa.getCapacidad()) {
            throw new IllegalArgumentException("Los comensales superan la capacidad de la mesa "
                    + mesa.getNumeroMesa() + " (" + mesa.getCapacidad() + ")");
        }
    }
}
