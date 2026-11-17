// Lecturas del catálogo: 50 por segundo durante 30 s, sobre 4 inventos (unos pocos muy pedidos,
// como un catálogo real). Compara CACHE=none con CACHE=redis o CACHE=local.
import http from 'k6/http'
import { check } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8101'
const CACHE = __ENV.CACHE || 'redis'
const SKUS = ['INV-001', 'INV-002', 'INV-003', 'INV-004']

export const options = {
  scenarios: {
    lecturas: { executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s', duration: '30s',
                preAllocatedVUs: 30, maxVUs: 100 },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
}

export default function () {
  const sku = SKUS[Math.floor(Math.random() * SKUS.length)]
  const res = http.get(`${BASE_URL}/api/inventions/${sku}?cache=${CACHE}`)
  check(res, { '200': (r) => r.status === 200 })
}
