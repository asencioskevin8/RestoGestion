-- Corrección de textos insertados con una codificación incorrecta.
-- Ejecutar usando el cliente MySQL con --default-character-set=utf8mb4.

SET NAMES utf8mb4;

UPDATE categorias
SET nombre_categoria = 'Cevichería'
WHERE nombre_categoria = 'CevicherÃ­a';

UPDATE productos
SET nombre_producto = 'Ceviche Clásico'
WHERE nombre_producto = 'Ceviche ClÃ¡sico';

UPDATE productos
SET nombre_producto = 'Ceviche de Camarón'
WHERE nombre_producto = 'Ceviche de CamarÃ³n';

UPDATE productos
SET nombre_producto = 'Chicharrón de Pescado'
WHERE nombre_producto = 'ChicharrÃ³n de Pescado';

SELECT c.nombre_categoria, p.nombre_producto
FROM productos p
JOIN categorias c ON c.id_categoria = p.id_categoria
WHERE c.nombre_categoria = 'Cevichería'
ORDER BY p.nombre_producto;
