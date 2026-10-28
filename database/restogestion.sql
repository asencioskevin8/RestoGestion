

CREATE DATABASE IF NOT EXISTS restogestion
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE restogestion;

-- Desactivar temporalmente revisión de llaves foráneas para regenerar limpio
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS comprobantes_pago;
DROP TABLE IF EXISTS sesiones_caja;
DROP TABLE IF EXISTS metodos_pago;
DROP TABLE IF EXISTS detalles_pedido;
DROP TABLE IF EXISTS pedidos;
DROP TABLE IF EXISTS mesas;
DROP TABLE IF EXISTS productos;
DROP TABLE IF EXISTS categorias;
DROP TABLE IF EXISTS usuarios;
DROP TABLE IF EXISTS roles;
SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------
-- 1. ROLES
-- ----------------------------------------------------------
CREATE TABLE roles (
    id_rol INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE,
    descripcion VARCHAR(150),
    activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE = InnoDB;

-- ----------------------------------------------------------
-- 2. USUARIOS
-- ----------------------------------------------------------
CREATE TABLE usuarios (
    id_usuario INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_rol INT UNSIGNED NOT NULL,
    nombre_completo VARCHAR(120) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    estado ENUM('ACTIVO', 'INACTIVO') NOT NULL DEFAULT 'ACTIVO',
    creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuarios_rol
        FOREIGN KEY (id_rol) REFERENCES roles (id_rol),
    INDEX idx_usuarios_rol (id_rol)
) ENGINE = InnoDB;

-- ----------------------------------------------------------
-- 3. CATEGORIAS
-- ----------------------------------------------------------
CREATE TABLE categorias (
    id_categoria INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre_categoria VARCHAR(60) NOT NULL UNIQUE,
    orden_menu SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE = InnoDB;

-- ----------------------------------------------------------
-- 4. PRODUCTOS
-- ----------------------------------------------------------
CREATE TABLE productos (
    id_producto INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_categoria INT UNSIGNED NOT NULL,
    nombre_producto VARCHAR(120) NOT NULL,
    descripcion VARCHAR(255),
    precio_unitario DECIMAL(10,2) NOT NULL,
    stock_actual DECIMAL(10,2) NOT NULL DEFAULT 0,
    stock_minimo DECIMAL(10,2) NOT NULL DEFAULT 5,
    controla_stock BOOLEAN NOT NULL DEFAULT TRUE,
    disponible BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_productos_precio CHECK (precio_unitario >= 0),
    CONSTRAINT chk_productos_stock CHECK (stock_actual >= 0 AND stock_minimo >= 0),
    CONSTRAINT fk_productos_categoria
        FOREIGN KEY (id_categoria) REFERENCES categorias (id_categoria),
    INDEX idx_productos_categoria (id_categoria),
    INDEX idx_productos_disponible (disponible)
) ENGINE = InnoDB;

-- ----------------------------------------------------------
-- 5. MESAS
-- ----------------------------------------------------------
CREATE TABLE mesas (
    id_mesa INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    numero_mesa VARCHAR(10) NOT NULL UNIQUE,
    capacidad TINYINT UNSIGNED NOT NULL,
    estado ENUM('LIBRE', 'OCUPADA', 'RESERVADA', 'CUENTA', 'LIMPIEZA') NOT NULL DEFAULT 'LIBRE',
    comensales_actuales TINYINT UNSIGNED NOT NULL DEFAULT 0,
    fecha_apertura DATETIME NULL,
    CONSTRAINT chk_mesas_capacidad CHECK (capacidad > 0),
    CONSTRAINT chk_mesas_comensales CHECK (comensales_actuales <= capacidad)
) ENGINE = InnoDB;

-- ----------------------------------------------------------
-- 6. PEDIDOS
-- ----------------------------------------------------------
CREATE TABLE pedidos (
    id_pedido INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_mesa INT UNSIGNED NULL,
    id_usuario_mesero INT UNSIGNED NULL,
    origen ENUM('SALON', 'CLIENTE', 'DELIVERY') NOT NULL DEFAULT 'SALON',
    estado_pedido ENUM('PENDIENTE', 'EN_PREPARACION', 'LISTO', 'ENTREGADO', 'CANCELADO') NOT NULL DEFAULT 'PENDIENTE',
    fecha_hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_calculado DECIMAL(10,2) NOT NULL DEFAULT 0,
    observaciones VARCHAR(255),
    CONSTRAINT chk_pedidos_total CHECK (total_calculado >= 0),
    CONSTRAINT fk_pedidos_mesa
        FOREIGN KEY (id_mesa) REFERENCES mesas (id_mesa),
    CONSTRAINT fk_pedidos_mesero
        FOREIGN KEY (id_usuario_mesero) REFERENCES usuarios (id_usuario),
    INDEX idx_pedidos_mesa (id_mesa),
    INDEX idx_pedidos_estado (estado_pedido),
    INDEX idx_pedidos_fecha (fecha_hora)
) ENGINE = InnoDB;

-- ----------------------------------------------------------
-- 7. DETALLES_PEDIDO
-- ----------------------------------------------------------
CREATE TABLE detalles_pedido (
    id_detalle INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT UNSIGNED NOT NULL,
    id_producto INT UNSIGNED NOT NULL,
    cantidad DECIMAL(10,2) NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    observaciones VARCHAR(255),
    estado_cocina ENUM('PENDIENTE', 'EN_PREPARACION', 'LISTO', 'ENTREGADO', 'CANCELADO') NOT NULL DEFAULT 'PENDIENTE',
    CONSTRAINT chk_detalles_cantidad CHECK (cantidad > 0),
    CONSTRAINT chk_detalles_precio CHECK (precio_unitario >= 0),
    CONSTRAINT fk_detalles_pedido
        FOREIGN KEY (id_pedido) REFERENCES pedidos (id_pedido) ON DELETE CASCADE,
    CONSTRAINT fk_detalles_producto
        FOREIGN KEY (id_producto) REFERENCES productos (id_producto),
    INDEX idx_detalles_pedido (id_pedido),
    INDEX idx_detalles_cocina (estado_cocina)
) ENGINE = InnoDB;

-- ----------------------------------------------------------
-- 8. METODOS_PAGO
-- ----------------------------------------------------------
CREATE TABLE metodos_pago (
    id_metodo INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre_metodo ENUM('EFECTIVO', 'TARJETA', 'YAPE', 'PLIN') NOT NULL UNIQUE,
    requiere_vuelto BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE = InnoDB;

-- ----------------------------------------------------------
-- 9. SESIONES_CAJA
-- ----------------------------------------------------------
CREATE TABLE sesiones_caja (
    id_sesion INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_usuario_cajero INT UNSIGNED NOT NULL,
    fecha_apertura DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre DATETIME NULL,
    monto_inicial DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_ventas DECIMAL(10,2) NOT NULL DEFAULT 0,
    saldo_final_fisico DECIMAL(10,2) NULL,
    descuadre DECIMAL(10,2) NULL,
    estado ENUM('ABIERTA', 'CERRADA') NOT NULL DEFAULT 'ABIERTA',
    CONSTRAINT chk_sesiones_monto CHECK (monto_inicial >= 0),
    CONSTRAINT fk_sesiones_usuario
        FOREIGN KEY (id_usuario_cajero) REFERENCES usuarios (id_usuario),
    INDEX idx_sesiones_estado (estado),
    INDEX idx_sesiones_fecha (fecha_apertura)
) ENGINE = InnoDB;

-- ----------------------------------------------------------
-- 10. COMPROBANTES_PAGO
-- ----------------------------------------------------------
CREATE TABLE comprobantes_pago (
    id_comprobante INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_pedido INT UNSIGNED NOT NULL UNIQUE,
    id_sesion INT UNSIGNED NOT NULL,
    id_metodo INT UNSIGNED NOT NULL,
    tipo_comprobante ENUM('BOLETA', 'FACTURA') NOT NULL,
    serie VARCHAR(10) NOT NULL DEFAULT 'B001',
    numero INT UNSIGNED NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    igv DECIMAL(10,2) NOT NULL DEFAULT 0,
    importe_total DECIMAL(10,2) NOT NULL,
    monto_recibido DECIMAL(10,2) NOT NULL,
    vuelto_entregado DECIMAL(10,2) NOT NULL DEFAULT 0,
    fecha_emision DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_comprobante_serie_numero UNIQUE (serie, numero),
    CONSTRAINT chk_comprobantes_importes CHECK (
        subtotal >= 0 AND igv >= 0 AND importe_total >= 0 AND
        monto_recibido >= importe_total AND vuelto_entregado >= 0
    ),
    CONSTRAINT fk_comprobantes_pedido
        FOREIGN KEY (id_pedido) REFERENCES pedidos (id_pedido),
    CONSTRAINT fk_comprobantes_sesion
        FOREIGN KEY (id_sesion) REFERENCES sesiones_caja (id_sesion),
    CONSTRAINT fk_comprobantes_metodo
        FOREIGN KEY (id_metodo) REFERENCES metodos_pago (id_metodo),
    INDEX idx_comprobantes_pedido (id_pedido),
    INDEX idx_comprobantes_sesion (id_sesion),
    INDEX idx_comprobantes_fecha (fecha_emision)
) ENGINE = InnoDB;

-- ==========================================================
-- DATOS INICIALES 
-- ==========================================================

-- Roles
INSERT INTO roles (nombre, descripcion) VALUES
    ('ADMINISTRADOR', 'Acceso completo y configuración del sistema'),
    ('CAJERO', 'Control de apertura, cobranza y arqueo de caja'),
    ('COCINERO', 'Monitor de preparación y despacho de pedidos'),
    ('MESERO', 'Control de mesas y toma de comandas en salón'),
    ('CLIENTE', 'Consulta del menú y pedidos por autoservicio');

-- Usuarios
INSERT INTO usuarios (id_rol, nombre_completo, username, password_hash, estado) VALUES
    (1, 'Kevin Asencios', 'admin', '$2a$10$ZzdAXCQQSa5SGgWtJFlI6eB.4oQcIYFF9/41k/gF4B3ws9pY0c.N2', 'ACTIVO'),
    (2, 'Brayan Uriarte', 'cajero', '$2a$10$ZzdAXCQQSa5SGgWtJFlI6eB.4oQcIYFF9/41k/gF4B3ws9pY0c.N2', 'ACTIVO'),
    (3, 'Jean Carlos Rojas', 'cocinero', '$2a$10$ZzdAXCQQSa5SGgWtJFlI6eB.4oQcIYFF9/41k/gF4B3ws9pY0c.N2', 'ACTIVO'),
    (4, 'Piero Paucar', 'mesero1', '$2a$10$ZzdAXCQQSa5SGgWtJFlI6eB.4oQcIYFF9/41k/gF4B3ws9pY0c.N2', 'ACTIVO'),
    (4, 'Andriv Lavado', 'mesero2', '$2a$10$ZzdAXCQQSa5SGgWtJFlI6eB.4oQcIYFF9/41k/gF4B3ws9pY0c.N2', 'ACTIVO');

-- Categorías
INSERT INTO categorias (nombre_categoria, orden_menu) VALUES
    ('Entradas', 1),
    ('Fondos', 2),
    ('Bebidas', 3),
    ('Postres', 4);

-- Métodos de Pago
INSERT INTO metodos_pago (nombre_metodo, requiere_vuelto) VALUES
    ('EFECTIVO', TRUE),
    ('TARJETA', FALSE),
    ('YAPE', FALSE),
    ('PLIN', FALSE);

-- Mesas del Salón
INSERT INTO mesas (numero_mesa, capacidad, estado, comensales_actuales) VALUES
    ('M01', 4, 'OCUPADA', 3),
    ('M02', 2, 'LIBRE', 0),
    ('M03', 6, 'CUENTA', 4),
    ('M04', 4, 'RESERVADA', 0),
    ('M05', 4, 'LIMPIEZA', 0),
    ('M06', 8, 'LIBRE', 0),
    ('M07', 2, 'OCUPADA', 2),
    ('M08', 4, 'LIBRE', 0);

-- Catálogo de 16 Platos y Bebidas
INSERT INTO productos (id_categoria, nombre_producto, descripcion, precio_unitario, stock_actual, stock_minimo) VALUES
    -- Entradas
    (1, 'Tequeños con Guacamole', '6 tequeños crocantes rellenos de queso con guacamole', 8.50, 20, 5),
    (1, 'Ceviche Clásico', 'Pescado del día con choclo, camote y canchita', 18.00, 12, 4),
    (1, 'Anticuchos de Corazón', '2 palitos con papas doradas y salsa de ají rocoto', 14.00, 15, 5),
    (1, 'Papa a la Huancaína', 'Papas sancochadas bañadas en crema huancaína tradicional', 10.00, 18, 5),
    -- Fondos
    (2, '1/4 Pollo a la Brasa', 'Pierna o pecho con papas fritas y ensalada clásica', 22.00, 30, 8),
    (2, 'Lomo Saltado', 'Trozos de lomo fino salteados al wok con arroz y papas', 28.00, 20, 5),
    (2, 'Ají de Gallina', 'Pechuga deshilachada en crema de ají amarillo con arroz', 24.00, 15, 4),
    (2, 'Arroz Chaufa de Pollo', 'Arroz frito al estilo chifa con trozos de pollo y cebolla china', 16.00, 25, 5),
    -- Bebidas
    (3, 'Jarra Chicha Morada (1L)', 'Preparada con maíz morado, piña, manzana y canela', 12.00, 40, 10),
    (3, 'Inca Kola 500ml', 'Gaseosa clásica de sabor nacional helada', 5.00, 60, 15),
    (3, 'Agua Mineral 625ml', 'Agua sin gas San Mateo', 3.50, 80, 20),
    (3, 'Jarra Limonada Frozzen (1L)', 'Refrescante limonada frappeada', 8.00, 35, 10),
    -- Postres
    (4, 'Suspiro a la Limeña', 'Dulce tradicional con merengue al oporto y canela', 9.00, 10, 3),
    (4, 'Porción de Picarones', '4 picarones bañados en miel de chancaca con higos', 7.50, 14, 4),
    (4, 'Torta de Chocolate', 'Bizcocho húmedo relleno de fudge artesanal', 11.00, 8, 3),
    (4, 'Crema Volteada', 'Clásica crema horneada con caramelo dorado', 8.50, 12, 3);

-- Sesión de Caja Abierta de Prueba
INSERT INTO sesiones_caja (id_usuario_cajero, monto_inicial, total_ventas, estado) VALUES
    (2, 200.00, 0.00, 'ABIERTA');

-- Vista de la Carta para consultas rápidas
CREATE OR REPLACE VIEW vista_carta AS
SELECT
    p.id_producto,
    c.nombre_categoria,
    p.nombre_producto,
    p.descripcion,
    p.precio_unitario,
    p.stock_actual,
    p.stock_minimo,
    p.disponible
FROM productos p
JOIN categorias c ON p.id_categoria = c.id_categoria
WHERE p.disponible = TRUE;
