import { describe, expect, it } from 'vitest'
import { probePublicApi } from './cors'

describe('probePublicApi', () => {
  it('reports success when the browser lets the cross-origin call through', async () => {
    const fakeFetch = async () => new Response(JSON.stringify({ service: 'catalog', environment: 'prod' }))

    const result = await probePublicApi('http://localhost:8201', fakeFetch)

    expect(result).toEqual({ ok: true, detail: 'catalog respondió desde prod' })
  })

  it('explains that a network error on a cross-origin call is usually CORS', async () => {
    const fakeFetch = async () => {
      throw new TypeError('Failed to fetch')
    }

    const result = await probePublicApi('http://localhost:8201', fakeFetch)

    expect(result.ok).toBe(false)
    expect(result.detail).toContain('CORS')
  })

  it('reports HTTP errors as such', async () => {
    const fakeFetch = async () => new Response('nope', { status: 403 })

    expect(await probePublicApi('http://localhost:8201', fakeFetch)).toEqual({ ok: false, detail: 'HTTP 403' })
  })
})
