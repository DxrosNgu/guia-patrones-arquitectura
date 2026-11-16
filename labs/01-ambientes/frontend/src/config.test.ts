import { describe, expect, it } from 'vitest'
import { loadConfig, findEnvironmentMismatches } from './config'

describe('loadConfig', () => {
  it('reads the runtime config injected by /config.js', () => {
    const win = { __APP_CONFIG__: { environment: 'prod', appTitle: 'Mercado', publicApiUrl: 'http://api' } }

    expect(loadConfig(win)).toEqual({ environment: 'prod', appTitle: 'Mercado', publicApiUrl: 'http://api' })
  })

  it('falls back to safe defaults when /config.js did not load', () => {
    expect(loadConfig({})).toEqual({ environment: 'desconocido', appTitle: 'Mercado de Inventos', publicApiUrl: '' })
  })

  it('fills missing keys with defaults', () => {
    expect(loadConfig({ __APP_CONFIG__: { environment: 'dev' } }).appTitle).toBe('Mercado de Inventos')
  })
})

describe('findEnvironmentMismatches', () => {
  it('returns nothing when every service runs in the same environment', () => {
    const services = [
      { service: 'catalog', environment: 'dev' },
      { service: 'inventory', environment: 'dev' },
    ]

    expect(findEnvironmentMismatches('dev', services)).toEqual([])
  })

  it('lists the services whose environment differs from the frontend', () => {
    const services = [
      { service: 'catalog', environment: 'prod' },
      { service: 'inventory', environment: 'dev' },
    ]

    expect(findEnvironmentMismatches('dev', services)).toEqual(['catalog (prod)'])
  })

  it('ignores services that could not be reached', () => {
    expect(findEnvironmentMismatches('dev', [null, { service: 'inventory', environment: 'dev' }])).toEqual([])
  })
})
