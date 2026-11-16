// Demostración de CORS (solo prod).
//
// En local y dev el navegador habla con UN solo origen: nginx reenvía /api/* a los
// servicios, así que no hay llamadas entre orígenes y CORS no aplica.
// En prod, además, el catálogo se publica como API pública en otro origen
// (http://localhost:8201). Cuando esta página la llama directo, el NAVEGADOR
// pregunta: ¿la API permite que http://localhost:3200 lea su respuesta?
// La API contesta con Access-Control-Allow-Origin, o el navegador bloquea la lectura.

export interface ProbeResult {
  ok: boolean
  detail: string
}

type FetchLike = (input: string, init?: RequestInit) => Promise<Response>

export async function probePublicApi(baseUrl: string, fetchImpl: FetchLike = fetch): Promise<ProbeResult> {
  try {
    const response = await fetchImpl(`${baseUrl}/api/info`)
    if (!response.ok) {
      return { ok: false, detail: `HTTP ${response.status}` }
    }
    const info = (await response.json()) as { service: string; environment: string }
    return { ok: true, detail: `${info.service} respondió desde ${info.environment}` }
  } catch (error) {
    // El navegador NO dice "CORS" en el error (por seguridad): solo "Failed to fetch".
    // El motivo real aparece en la consola de DevTools.
    return {
      ok: false,
      detail: `${(error as Error).message}. En una llamada a otro origen suele ser CORS: mira la consola del navegador.`,
    }
  }
}
