-- El catálogo NUEVO: su propia base. Se llena una vez copiando el del legado (ver CatalogImport).
CREATE TABLE products (
    sku   VARCHAR(20) PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL
);
