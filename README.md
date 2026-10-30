# RestoGestión Web - Sistema Integral de Gestión para Restaurantes

Proyecto universitario para el curso **Marcos de Desarrollo Web (100000SI57)** - UTP (Ciclo 2026).

---

## 🛠️ Stack Tecnológico
* **Lenguaje:** Java 21 LTS
* **Framework Back-end:** Spring Boot 3.5.6 (Spring Web, Spring Data JPA, Hibernate, Bean Validation)
* **Base de Datos:** MySQL 8.x (InnoDB, utf8mb4)
* **Front-end:** HTML5, CSS3 modular, Bootstrap Icons 1.11.3
* **Gestor de dependencias:** Maven

---

## 👥 Reparto Modular del Equipo 

| Integrante | 
| :--- | 
| **Kevin Asencios** | 
| **Andriv Lavado** | 
| **Edilson Magallanes** |
| **Piero Paucar** | 
| **Jean Carlos Rojas** | 
| **Brayan Uriarte** | 

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
spring.datasource.password=root
```

Si la contraseña de MySQL es diferente, reemplaza `root` por la contraseña configurada en tu instalación local.

### 3. Ejecutar la Aplicación
* **Desde VS Code:** Presiona `F5` o haz clic en *"Run"* sobre `RestoGestionApplication.java`.
* **Desde terminal:**
  ```bash
  mvn clean compile
  mvn spring-boot:run
  ```
* Abre tu navegador en: **`http://localhost:8080`**

### 4. Acceso al sistema
La aplicación solicita autenticación y restringe las vistas según el rol. Para las cuentas de prueba, la contraseña temporal de presentación es `1234`:

| Usuario | Rol | Acceso inicial |
| :--- | :--- | :--- |
| `admin` | Administrador | Todas las vistas |
| `cajero` | Cajero | Caja |
| `cocinero` | Cocinero | Cocina |
| `mesero1`, `mesero2` | Mesero | Mesas y pedidos |

Para esta presentación, todos los usuarios utilizan la contraseña temporal `1234`. Las contraseñas se almacenan como hashes BCrypt en la columna `password_hash` de la tabla `usuarios`.

El administrador puede registrar y editar productos desde **Menú**. Los productos se guardan mediante JPA en la tabla `productos` de MySQL.

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
