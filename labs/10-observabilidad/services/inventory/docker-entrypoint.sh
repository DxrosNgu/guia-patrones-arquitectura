#!/bin/sh
# Convención *_FILE (la misma que usan las imágenes oficiales de Postgres o MySQL):
#
#   SUPPLIER_API_KEY_FILE=/run/secrets/supplier_api_key
#
# hace que se exporte SUPPLIER_API_KEY con el CONTENIDO de ese archivo.
# Así el secreto nunca aparece en `docker inspect` ni en el compose: solo la ruta.
set -eu

for file_var in $(env | sed -n 's/^\([A-Za-z0-9_]*_FILE\)=.*/\1/p'); do
  var="${file_var%_FILE}"
  eval "path=\${$file_var}"
  if [ ! -r "$path" ]; then
    echo "ERROR: $file_var apunta a '$path', pero ese archivo no existe o no se puede leer." >&2
    exit 1
  fi
  export "$var=$(cat "$path")"
  unset "$file_var"
done

exec "$@"
