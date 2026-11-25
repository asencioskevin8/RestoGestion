package com.restogestion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "metodos_pago")
public class MetodoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metodo")
    private Long idMetodo;

    @NotBlank(message = "El nombre del método de pago es obligatorio")
    @Size(max = 20)
    @Column(name = "nombre_metodo", nullable = false, unique = true, length = 20)
    private String nombreMetodo;

    @Column(name = "requiere_vuelto", nullable = false)
    private Boolean requiereVuelto = false;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    public MetodoPago() {}

    public MetodoPago(String nombreMetodo, Boolean requiereVuelto) {
        this.nombreMetodo = nombreMetodo;
        this.requiereVuelto = requiereVuelto;
        this.activo = true;
    }

    public Long getIdMetodo() { return idMetodo; }
    public void setIdMetodo(Long idMetodo) { this.idMetodo = idMetodo; }

    public String getNombreMetodo() { return nombreMetodo; }
    public void setNombreMetodo(String nombreMetodo) { this.nombreMetodo = nombreMetodo; }

    public Boolean getRequiereVuelto() { return requiereVuelto; }
    public void setRequiereVuelto(Boolean requiereVuelto) { this.requiereVuelto = requiereVuelto; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
