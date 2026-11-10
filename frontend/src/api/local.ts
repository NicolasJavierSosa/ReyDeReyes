// Endpoints del sistema local (Spring Boot en esta PC)
import { get, patch, post, put } from './http'
import type {
  CajaCerrada, Categoria, CierreCaja, Cliente, ComboRequest, Conflicto, EstadoCaja, EstadoSync, Estadisticas,
  MetodoPago, MovimientoStock, MovimientoTesoreria, OpenFoodFacts, Producto, ProductoRequest, ProductoVenta,
  Proveedor, ResultadoSync, ResumenTesoreria, SolicitudCompra, Uuid, VentaCheckoutRespuesta, VentaDetalle, VentaResumen,
} from './tipos'

const q = (params: Record<string, string | number | null | undefined>) => {
  const s = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== null && v !== undefined && v !== '') s.set(k, String(v))
  })
  const texto = s.toString()
  return texto ? `?${texto}` : ''
}

export const auth = {
  async login(usuario: string, clave: string): Promise<boolean> {
    const cuerpo = new URLSearchParams({ username: usuario, password: clave })
    const r = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: cuerpo,
    })
    return r.ok
  },
  logout: () => fetch('/api/auth/logout', { method: 'POST' }),
  cuenta: () => get<{ username: string; name: string }>('/api/cuenta'),
  cambiarClave: (currentPassword: string, newPassword: string) =>
    post<void>('/api/cuenta/clave', { currentPassword, newPassword }),
}

export const productosApi = {
  listar: () => get<Producto[]>('/api/productos'),
  crear: (p: ProductoRequest) => post<Producto>('/api/productos', p),
  actualizar: (id: Uuid, p: ProductoRequest) => put<Producto>(`/api/productos/${id}`, p),
  crearCombo: (c: ComboRequest) => post<Producto>('/api/productos/combos', c),
  actualizarCombo: (id: Uuid, c: ComboRequest) => put<Producto>(`/api/productos/combos/${id}`, c),
  cambiarEstado: (id: Uuid, active: boolean) => patch<Producto>(`/api/productos/${id}/estado`, { active }),
  ajustarStock: (id: Uuid, newStock: number, reason: string) =>
    post<Producto>(`/api/productos/${id}/ajuste-stock`, { newStock, reason }),
  movimientos: (id: Uuid) => get<MovimientoStock[]>(`/api/productos/${id}/movimientos`),
  porCodigo: (codigo: string) => get<ProductoVenta>(`/api/productos/venta/codigo/${encodeURIComponent(codigo)}`),
  buscar: (texto: string, categoriaId?: Uuid | null) =>
    get<ProductoVenta[]>(`/api/productos/venta/buscar${q({ q: texto, categoriaId })}`),
  openFoodFacts: (codigo: string) => get<OpenFoodFacts>(`/api/openfoodfacts/${encodeURIComponent(codigo)}`),
}

export const categoriasApi = {
  listar: (status: 'active' | 'inactive' | 'all' = 'all') => get<Categoria[]>(`/api/categorias?status=${status}`),
  crear: (nombre: string) => post<Categoria>('/api/categorias', { nombre }),
  actualizar: (id: Uuid, nombre: string) => put<Categoria>(`/api/categorias/${id}`, { nombre }),
  cambiarEstado: (id: Uuid, activa: boolean) => patch<void>(`/api/categorias/${id}/estado`, { activa }),
}

export const proveedoresApi = {
  listar: (status = 'all') => get<Proveedor[]>(`/api/proveedores?status=${status}`),
  crear: (p: { phone: string; name: string; active?: boolean }) => post<Proveedor>('/api/proveedores', p),
  actualizar: (telefono: string, p: { phone: string; name: string; active?: boolean }) =>
    put<Proveedor>(`/api/proveedores/${encodeURIComponent(telefono)}`, p),
  cambiarEstado: (telefono: string, active: boolean) =>
    patch<Proveedor>(`/api/proveedores/${encodeURIComponent(telefono)}/estado`, { active }),
}

export const comprasApi = {
  listar: (status: 'pending' | 'all' = 'pending') => get<SolicitudCompra[]>(`/api/solicitudes-compra?status=${status}`),
  crear: (supplierId: string, items: { productId: Uuid; qty: number }[]) =>
    post<SolicitudCompra>('/api/solicitudes-compra', { supplierId, items }),
  recepcionar: (id: Uuid, items: { itemId: Uuid; receivedQty: number }[]) =>
    put<SolicitudCompra>(`/api/solicitudes-compra/${id}/recepcion`, { items }),
}

export const clientesApi = {
  listar: (status = 'all') => get<Cliente[]>(`/api/clientes?status=${status}`),
  crear: (c: Omit<Cliente, 'id' | 'active'>) => post<Cliente>('/api/clientes', c),
  actualizar: (id: Uuid, c: Omit<Cliente, 'id' | 'active'>) => put<Cliente>(`/api/clientes/${id}`, c),
  cambiarEstado: (id: Uuid, active: boolean) => patch<Cliente>(`/api/clientes/${id}/estado`, { active }),
}

export const metodosPagoApi = {
  listar: (soloActivos = true) => get<MetodoPago[]>(`/api/metodos-pago?onlyActive=${soloActivos}`),
  crear: (m: { code: string; name: string; affectsCash: boolean; active: boolean; order: number }) =>
    post<MetodoPago>('/api/metodos-pago', m),
  actualizar: (id: Uuid, m: { code: string; name: string; affectsCash: boolean; active: boolean; order: number }) =>
    put<MetodoPago>(`/api/metodos-pago/${id}`, m),
}

export const ventasApi = {
  cobrar: (cuerpo: {
    items: { barcode: string; quantity: number }[]
    discount: number
    payment: { method: string; amountReceived: number | null }
  }) => post<VentaCheckoutRespuesta>('/api/ventas/checkout', cuerpo),
  detalle: (id: Uuid) => get<VentaDetalle>(`/api/ventas/${id}`),
  anular: (id: Uuid) => patch<void>(`/api/ventas/${id}/anular`),
  listar: (desde: string, hasta: string) => get<VentaResumen[]>(`/api/ventas${q({ desde, hasta })}`),
}

export const tesoreriaApi = {
  estadoCaja: () => get<EstadoCaja>('/api/tesoreria/caja/estado'),
  abrirCaja: (amount: number, notes: string) => post<{ id: Uuid }>('/api/tesoreria/caja/apertura', { amount, notes }),
  cerrarCaja: (bills: { denomination: number; quantity: number }[], notes: string) =>
    post<CierreCaja>('/api/tesoreria/caja/cierre', { bills, notes }),
  resumen: () => get<ResumenTesoreria>('/api/tesoreria/resumen'),
  movimientos: () => get<MovimientoTesoreria[]>('/api/tesoreria/movimientos'),
  movimientosDeCaja: (id: Uuid) => get<MovimientoTesoreria[]>(`/api/tesoreria/cajas/${id}/movimientos`),
  registrarManual: (m: { type: 'INGRESO' | 'EGRESO'; concept: string; detail: string; amount: number }) =>
    post<MovimientoTesoreria>('/api/tesoreria/movimientos/manual', m),
  anularMovimiento: (id: Uuid) => patch<void>(`/api/tesoreria/movimientos/${id}/anular`),
  cajasCerradas: (desde: string | null, hasta: string | null, page = 0, size = 20) =>
    get<{ items: CajaCerrada[]; page: number; size: number; hasMore: boolean }>(
      `/api/tesoreria/cajas/cerradas${q({ from: desde, to: hasta, page, size })}`,
    ),
}

export const estadisticasApi = {
  obtener: (period: string) => get<Estadisticas>(`/api/estadisticas?period=${encodeURIComponent(period)}`),
}

export const syncApi = {
  estado: () => get<EstadoSync>('/api/sync/estado'),
  ahora: () => post<ResultadoSync>('/api/sync/ahora'),
  conflictos: () => get<Conflicto[]>('/api/sync/conflictos'),
  marcarRevisado: (id: Uuid) => patch<void>(`/api/sync/conflictos/${id}/revisado`),
  restaurar: () => post<ResultadoSync>('/api/sync/restaurar'),
}
