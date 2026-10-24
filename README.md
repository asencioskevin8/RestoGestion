# RestoGestión Web - Sistema Integral de Gestión para Restaurantes

Proyecto universitario para el curso **Marcos de Desarrollo Web (100000SI57)** - UTP (Ciclo 2026).

---

## 🛠️ Stack Tecnológico
* **Lenguaje:** Java 21 LTS
* **Framework Back-end:** Spring Boot 3.5.6 (Spring Web, Spring Data JPA, Hibernate, Bean Validation)
* **Base de Datos:** MySQL 8.x (InnoDB, utf8mb4)
* **Front-end:** HTML5, CSS3 modular, Bootstrap Icons 1.11.3 (iconografía vectorial SVG profesional)
* **Gestor de dependencias:** Maven

---

## 👥 Reparto Modular del Equipo (APF3 - Semana 12)

| Integrante | Rol / Módulo | Clases / Archivos Asignados | Sección del Informe |
| :--- | :--- | :--- | :--- |
| **Kevin Asencios** | **Líder de Base de Datos y Persistencia** | `database/restogestion.sql`, 10 Entidades `@Entity`, 10 `JpaRepository` | **Apartado 3.4** (Diccionario de datos y relaciones) |
| **Andriv Lavado** | **Salón y Mapa de Mesas** (`RF-05`) | `MesaService.java`, `MesaController.java`, `templates/mesas/` | **Apartado 3.5** (Ficha de Salón) + 3.6.1 |
| **Edilson Magallanes** | **Carta, Menú e Inventario** (`RF-02`, `RF-03`) | `ProductoService.java`, `ProductoController.java`, `templates/menu/` | **Apartado 3.5** (Ficha de Menú) + 3.6.1 |
| **Piero Paucar** | **Comandas Digitales** (`RF-06`, `RF-07`, `RF-08`) | `PedidoService.java`, `PedidoController.java`, `templates/pedidos/` | **Apartado 3.5** (Ficha de Comanda) + 3.6.2 (Secuencia) |
| **Jean Carlos Rojas** | **Monitor de Cocina (KDS)** (`RF-09`, `RF-10`) | `CocinaService.java`, `CocinaController.java`, `templates/cocina/` | **Apartado 3.5** (Ficha de Cocina) + 3.6.1 |
| **Brayan Uriarte** | **Facturación, Cobro y Caja** (`RF-11`, `RF-12`, `RF-04`) | `CajaService.java`, `CajaController.java`, `templates/caja/` | **Apartado 3.5** (Ficha de Cobro/Arqueo) + 3.6.1 |

---

## 🚀 Puesta en Marcha Rápida

### 1. Base de Datos MySQL
1. Abre MySQL Workbench o phpMyAdmin.
2. Abre y ejecuta el archivo:
   `database/restogestion.sql`
   *(Crea la base de datos `restogestion` con 10 tablas y seeders de prueba).*

### 2. Configuración en `application.properties`
Abre `src/main/resources/application.properties` y verifica la contraseña de tu MySQL:
```properties
spring.datasource.username=root
spring.datasource.password=tu_contraseña
```

### 3. Ejecutar la Aplicación
* **Desde VS Code:** Presiona `F5` o haz clic en *"Run"* sobre `RestoGestionApplication.java`.
* **Desde terminal:**
  ```bash
  mvn clean compile
  mvn spring-boot:run
  ```
* Abre tu navegador en: **`http://localhost:8080`**

---

## 📁 Estructura del Código
```
RestoGestion/
├── database/
│   └── restogestion.sql        # Script MySQL de 10 tablas + Seeders
├── src/main/java/com/restogestion/
│   ├── controller/             # Controladores MVC y REST API
│   ├── entity/                 # 10 Entidades JPA mapeadas
│   ├── repository/             # 10 Repositorios Spring Data JPA
│   ├── service/                # Lógica de negocio transaccional (@Service)
│   └── RestoGestionApplication.java
└── src/main/resources/
    ├── static/css/             # Estilos limpios y consistentes
    ├── templates/              # Vistas Thymeleaf
    └── application.properties  # Conexión MySQL y puertos
```
