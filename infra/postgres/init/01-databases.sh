#!/bin/sh
# Se ejecuta UNA sola vez: cuando el volumen de datos está vacío.
# Para volver a ejecutarlo: docker compose down -v
set -eu

for db in $APP_DATABASES; do
  echo "Creando base y usuario: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<EOSQL
CREATE USER $db WITH PASSWORD '$db';
CREATE DATABASE $db OWNER $db;
-- Solo su dueño puede conectarse: así se ve "Database per Service".
REVOKE CONNECT ON DATABASE $db FROM PUBLIC;
EOSQL
done
