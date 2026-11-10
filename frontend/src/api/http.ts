import { router } from '@/router'

export class ErrorApi extends Error {
  constructor(message: string, public status: number) {
    super(message)
  }
}

/** fetch con JSON, mensajes de error legibles y redireccion al login si la sesion vencio. */
export async function http<T>(url: string, opciones: RequestInit & { json?: unknown } = {}): Promise<T> {
  const { json, headers, ...resto } = opciones
  const init: RequestInit = { ...resto, headers: { ...(headers || {}) } }
  if (json !== undefined) {
    init.body = JSON.stringify(json)
    ;(init.headers as Record<string, string>)['Content-Type'] = 'application/json'
  }
  let respuesta: Response
  try {
    respuesta = await fetch(url, init)
  } catch {
    throw new ErrorApi('No se pudo conectar con el sistema. ¿Esta iniciado?', 0)
  }
  if (respuesta.status === 401 && !url.startsWith('/api/auth')) {
    if (router.currentRoute.value.name !== 'login') {
      router.push({ name: 'login', query: { volver: router.currentRoute.value.fullPath } })
    }
    throw new ErrorApi('La sesion vencio. Ingrese nuevamente.', 401)
  }
  if (!respuesta.ok) {
    let mensaje = `Error ${respuesta.status}`
    try {
      const cuerpo = await respuesta.json()
      if (cuerpo?.message) mensaje = cuerpo.message
      else if (cuerpo?.detail) mensaje = cuerpo.detail
    } catch {
      /* sin detalle */
    }
    throw new ErrorApi(mensaje, respuesta.status)
  }
  if (respuesta.status === 204) return undefined as T
  const texto = await respuesta.text()
  return (texto ? JSON.parse(texto) : undefined) as T
}

export const get = <T>(url: string) => http<T>(url)
export const post = <T>(url: string, json?: unknown) => http<T>(url, { method: 'POST', json })
export const put = <T>(url: string, json?: unknown) => http<T>(url, { method: 'PUT', json })
export const patch = <T>(url: string, json?: unknown) => http<T>(url, { method: 'PATCH', json })
