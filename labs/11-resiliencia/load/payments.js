// Carga constante sobre pagos: RATE pagos por segundo durante DURATION.
//   docker compose --profile load run --rm k6                       → camino resiliente
//   TARGET=naive docker compose --profile load run --rm k6          → sin protección
// Mientras corre, juega con el caos de la pasarela y mira el circuito en Grafana (README).
import http from 'k6/http'
import { check } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8104'
const TARGET = __ENV.TARGET ? `/${__ENV.TARGET}` : ''

export const options = {
  scenarios: {
    pagos: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    // Lo que importa al usuario: que responda rápido, aunque sea "pendiente".
    http_req_duration: ['p(95)<3000'],
  },
}

export default function () {
  const orderId = `k6-${__VU}-${__ITER}-${Date.now()}`
  const res = http.post(`${BASE_URL}/api/payments${TARGET}`, JSON.stringify({ orderId, amount: 25 }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '35s' })
  check(res, {
    'aprobado o pendiente (201/202)': (r) => r.status === 201 || r.status === 202,
  })
}
