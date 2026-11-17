/**
 * Lo que se COMPARTE entre el shell y los remotos en tiempo de ejecución. Lo importan los tres
 * vite.config.ts para que nadie lo configure distinto.
 *
 * singleton: una sola copia en el navegador. React lo exige (dos Reacts = hooks rotos), y el design
 * system también (una sola hoja de estilos y los mismos componentes en todas las pantallas).
 */
export const shared = {
  react: { singleton: true, requiredVersion: '19.3.1' },
  'react-dom': { singleton: true, requiredVersion: '19.3.1' },
  '@mercado/platform': { singleton: true, requiredVersion: '1.0.0' },
}
