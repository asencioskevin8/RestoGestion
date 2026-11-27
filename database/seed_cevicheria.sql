-- Datos iniciales adicionales para una carta de cevichería.
-- Script seguro para ejecutar sobre una base existente: no elimina tablas
-- y evita duplicar mesas o productos por su nombre.

-- Las mesas M01-M08 ya forman parte del seed principal. Estas inserciones
-- permiten completar las que falten sin modificar las que ya tienen pedidos.
INSERT INTO mesas (numero_mesa, capacidad, estado, comensales_actuales)
SELECT 'M01', 4, 'LIBRE', 0
WHERE NOT EXISTS (SELECT 1 FROM mesas WHERE numero_mesa = 'M01');

INSERT INTO mesas (numero_mesa, capacidad, estado, comensales_actuales)
SELECT 'M02', 2, 'LIBRE', 0
WHERE NOT EXISTS (SELECT 1 FROM mesas WHERE numero_mesa = 'M02');

INSERT INTO mesas (numero_mesa, capacidad, estado, comensales_actuales)
SELECT 'M03', 6, 'LIBRE', 0
WHERE NOT EXISTS (SELECT 1 FROM mesas WHERE numero_mesa = 'M03');

INSERT INTO mesas (numero_mesa, capacidad, estado, comensales_actuales)
SELECT 'M04', 4, 'LIBRE', 0
WHERE NOT EXISTS (SELECT 1 FROM mesas WHERE numero_mesa = 'M04');

INSERT INTO mesas (numero_mesa, capacidad, estado, comensales_actuales)
SELECT 'M05', 4, 'LIBRE', 0
WHERE NOT EXISTS (SELECT 1 FROM mesas WHERE numero_mesa = 'M05');

INSERT INTO mesas (numero_mesa, capacidad, estado, comensales_actuales)
SELECT 'M06', 8, 'LIBRE', 0
WHERE NOT EXISTS (SELECT 1 FROM mesas WHERE numero_mesa = 'M06');

INSERT INTO mesas (numero_mesa, capacidad, estado, comensales_actuales)
SELECT 'M07', 2, 'LIBRE', 0
WHERE NOT EXISTS (SELECT 1 FROM mesas WHERE numero_mesa = 'M07');

INSERT INTO mesas (numero_mesa, capacidad, estado, comensales_actuales)
SELECT 'M08', 4, 'LIBRE', 0
WHERE NOT EXISTS (SELECT 1 FROM mesas WHERE numero_mesa = 'M08');

-- Categoría específica para la carta de cevichería.
INSERT INTO categorias (nombre_categoria, orden_menu, activo)
VALUES ('Cevichería', 1, TRUE)
ON DUPLICATE KEY UPDATE activo = TRUE;

-- Productos de cevichería. La categoría se busca por nombre para que el
-- script funcione aunque el ID sea diferente en la base remota.
INSERT INTO productos
    (id_categoria, nombre_producto, descripcion, precio_unitario,
     stock_actual, stock_minimo, controla_stock, disponible,
     creado_en, actualizado_en)
SELECT c.id_categoria, x.nombre_producto, x.descripcion, x.precio_unitario,
       x.stock_actual, x.stock_minimo, TRUE, TRUE, NOW(), NOW()
FROM categorias c
JOIN (
    SELECT 'Ceviche Clásico' AS nombre_producto,
           'Pescado del día con limón, cebolla, ají, choclo, camote y cancha' AS descripcion,
           18.00 AS precio_unitario, 20.00 AS stock_actual, 5.00 AS stock_minimo
    UNION ALL SELECT 'Ceviche Mixto',
           'Pescado y mariscos frescos con guarniciones tradicionales',
           26.00, 15.00, 4.00
    UNION ALL SELECT 'Ceviche de Camarón',
           'Camarones marinados en limón con cebolla y camote',
           28.00, 12.00, 4.00
    UNION ALL SELECT 'Ceviche Carretillero',
           'Ceviche clásico acompañado de chicharrón de pota',
           24.00, 15.00, 4.00
    UNION ALL SELECT 'Leche de Tigre',
           'Concentrado de pescado, limón, ají y trozos de pescado',
           12.00, 20.00, 5.00
    UNION ALL SELECT 'Chicharrón de Pescado',
           'Trozos de pescado crocante con yuca y salsa criolla',
           22.00, 18.00, 5.00
    UNION ALL SELECT 'Arroz con Mariscos',
           'Arroz preparado con mariscos, ají amarillo y queso parmesano',
           27.00, 15.00, 4.00
    UNION ALL SELECT 'Jalea Mixta',
           'Pescado y mariscos crocantes con yuca y salsa criolla',
           30.00, 12.00, 4.00
    UNION ALL SELECT 'Causa Acevichada',
           'Causa de papa amarilla cubierta con ceviche de pescado',
           20.00, 15.00, 4.00
) x ON c.nombre_categoria = 'Cevichería'
WHERE NOT EXISTS (
    SELECT 1
    FROM productos p
    WHERE p.nombre_producto = x.nombre_producto
);

-- Verificación rápida.
SELECT numero_mesa, capacidad, estado
FROM mesas
ORDER BY numero_mesa;

SELECT c.nombre_categoria, p.nombre_producto, p.precio_unitario,
       p.stock_actual, p.disponible
FROM productos p
JOIN categorias c ON c.id_categoria = p.id_categoria
WHERE c.nombre_categoria = 'Cevichería'
ORDER BY p.nombre_producto;
