# Secretos de prod

Compose monta cada archivo en `/run/secrets/<nombre>` dentro del contenedor que lo pide. Los `*.txt` están en `.gitignore`: **nunca** se suben a Git.

Antes de levantar prod, créalos a partir de los ejemplos (y cambia los valores):

```bash
cp secrets/db_password.txt.example secrets/db_password.txt
cp secrets/supplier_api_key.txt.example secrets/supplier_api_key.txt
```

Si falta alguno, el contenedor que lo necesita no arranca y el error dice qué archivo falta.
