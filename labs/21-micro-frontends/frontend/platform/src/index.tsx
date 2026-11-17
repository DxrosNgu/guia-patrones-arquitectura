/**
 * Design system: componentes pequeños y tokens. Se COMPARTE como singleton en Module Federation:
 * el navegador lo carga una vez, aunque lo usen el shell y los dos remotos.
 */
import type { ButtonHTMLAttributes, ReactNode } from 'react'
import './tokens.css'

export function Button({ variant = 'secondary', className = '', ...props }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' }) {
  return <button {...props} className={`mi-button ${variant === 'primary' ? 'mi-button--primary' : ''} ${className}`} />
}

export function Card({ children, title }: { children: ReactNode; title?: ReactNode }) {
  return (
    <section className="mi-card">
      {title && <h2 style={{ margin: '0 0 0.6rem', fontSize: '1rem' }}>{title}</h2>}
      {children}
    </section>
  )
}

export function Badge({ children, label }: { children: ReactNode; label: string }) {
  return <span className="mi-badge" aria-label={label}>{children}</span>
}

export function Notice({ children }: { children: ReactNode }) {
  return <p className="mi-notice" role="status">{children}</p>
}

const pen = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })

export function Price({ value }: { value: number }) {
  return <span>{pen.format(value)}</span>
}

export const formatPrice = (value: number) => pen.format(value)

export { useLocation } from './url'
