import { describe, expect, it } from 'vitest'
import { mergeWithOptimistic, readYourWrites, waitByPolling, type HistoryOrder, type Provisional } from './history'

const order = (id: string, status = 'RECIBIDO'): HistoryOrder => ({
  id, createdAt: '2026-11-17T10:00:00Z', summary: '1 × Paraguas solar', total: 49.9, status, orderVersion: 1,
})

describe('mergeWithOptimistic', () => {
  it('shows a provisional row until the read side has the real one', () => {
    const provisional: Provisional = { id: 'o-2', summary: '1 × Taza', total: 25.5, createdAt: '2026-11-17T10:01:00Z' }

    const before = mergeWithOptimistic([order('o-1')], [provisional])
    const after = mergeWithOptimistic([order('o-2'), order('o-1')], [provisional])

    expect(before.map((o) => [o.id, o.status])).toEqual([['o-2', 'PROCESANDO'], ['o-1', 'RECIBIDO']])
    expect(after.map((o) => [o.id, o.status])).toEqual([['o-2', 'RECIBIDO'], ['o-1', 'RECIBIDO']])
  })
})

describe('waitByPolling', () => {
  it('asks again and again until the view has the order', async () => {
    let calls = 0
    const fetchImpl = async () => (++calls < 3 ? new Response('{}', { status: 404 }) : new Response(JSON.stringify(order('o-1'))))

    const result = await waitByPolling('o-1', fetchImpl, async () => {})

    expect(result.requests).toBe(3)
  })
})

describe('readYourWrites', () => {
  it('asks once for at least the version it wrote and lets the server wait', async () => {
    const urls: string[] = []
    const fetchImpl = async (url: string) => {
      urls.push(url)
      return new Response(JSON.stringify(order('o-1')))
    }

    const result = await readYourWrites('o-1', 1, fetchImpl)

    expect(urls).toEqual(['/api/history/o-1?minVersion=1&waitMs=8000'])
    expect(result.requests).toBe(1)
  })
})
