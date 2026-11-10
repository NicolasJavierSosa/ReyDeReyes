const moneda = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })
const cantidadFmt = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 3 })

export function dinero(valor: number | string | null | undefined): string {
  return moneda.format(Number(valor ?? 0) || 0)
}

export function cantidad(valor: number | string | null | undefined): string {
  return cantidadFmt.format(Number(valor ?? 0) || 0)
}

export function fechaHora(valor: string | null | undefined): string {
  if (!valor) return '-'
  return new Date(valor).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' })
}

export function fecha(valor: string | null | undefined): string {
  if (!valor) return '-'
  return new Date(valor).toLocaleDateString('es-AR')
}

export function hora(valor: string | null | undefined): string {
  if (!valor) return '-'
  return new Date(valor).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' })
}

export function redondearDinero(valor: number): number {
  return Math.round((valor + Number.EPSILON) * 100) / 100
}

export function redondearCantidad(valor: number): number {
  return Math.round((valor + Number.EPSILON) * 1000) / 1000
}

/** Texto sin acentos y en minusculas, para buscar. */
export function normalizar(texto: string | null | undefined): string {
  return (texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim()
}

export function hoyISO(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
