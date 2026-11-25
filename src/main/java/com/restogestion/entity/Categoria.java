package com.restogestion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "categorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria")
    private Long idCategoria;

    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 60)
    @Column(name = "nombre_categoria", nullable = false, unique = true, length = 60)
    private String nombreCategoria;

    @Column(name = "orden_menu", nullable = false)
    private Integer ordenMenu = 0;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    public Categoria() {}

    public Categoria(String nombreCategoria, Integer ordenMenu) {
        this.nombreCategoria = nombreCategoria;
        this.ordenMenu = ordenMenu;
        this.activo = true;
    }

    public Long getIdCategoria() { return idCategoria; }
    public void setIdCategoria(Long idCategoria) { this.idCategoria = idCategoria; }

    public String getNombreCategoria() { return nombreCategoria; }
    public void setNombreCategoria(String nombreCategoria) { this.nombreCategoria = nombreCategoria; }

    public Integer getOrdenMenu() { return ordenMenu; }
    public void setOrdenMenu(Integer ordenMenu) { this.ordenMenu = ordenMenu; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
