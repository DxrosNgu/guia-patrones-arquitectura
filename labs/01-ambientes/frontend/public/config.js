// Solo para `npm run dev`. En el contenedor, nginx REEMPLAZA este archivo al
// arrancar con los valores de las variables de entorno (nginx/40-runtime-config.sh).
window.__APP_CONFIG__ = {
  environment: 'local-host',
  appTitle: 'Mercado de Inventos',
}
