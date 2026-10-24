package com.restogestion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones_caja")
public class SesionCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sesion")
    private Long idSesion;

    @NotNull(message = "El cajero a cargo es obligatorio")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario_cajero", nullable = false)
    private Usuario usuarioCajero;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @NotNull(message = "El monto inicial de caja es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El monto inicial no puede ser negativo")
    @Column(name = "monto_inicial", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoInicial = BigDecimal.ZERO;

    @Column(name = "total_ventas", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalVentas = BigDecimal.ZERO;

    @Column(name = "saldo_final_fisico", precision = 10, scale = 2)
    private BigDecimal saldoFinalFisico;

    @Column(name = "descuadre", precision = 10, scale = 2)
    private BigDecimal descuadre;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "ABIERTA";

    @PrePersist
    public void prePersist() {
        this.fechaApertura = LocalDateTime.now();
    }

    public SesionCaja() {}

    public Long getIdSesion() { return idSesion; }
    public void setIdSesion(Long idSesion) { this.idSesion = idSesion; }

    public Usuario getUsuarioCajero() { return usuarioCajero; }
    public void setUsuarioCajero(Usuario usuarioCajero) { this.usuarioCajero = usuarioCajero; }

    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(LocalDateTime fechaApertura) { this.fechaApertura = fechaApertura; }

    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }

    public BigDecimal getMontoInicial() { return montoInicial; }
    public void setMontoInicial(BigDecimal montoInicial) { this.montoInicial = montoInicial; }

    public BigDecimal getTotalVentas() { return totalVentas; }
    public void setTotalVentas(BigDecimal totalVentas) { this.totalVentas = totalVentas; }

    public BigDecimal getSaldoFinalFisico() { return saldoFinalFisico; }
    public void setSaldoFinalFisico(BigDecimal saldoFinalFisico) { this.saldoFinalFisico = saldoFinalFisico; }

    public BigDecimal getDescuadre() { return descuadre; }
    public void setDescuadre(BigDecimal descuadre) { this.descuadre = descuadre; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
