// Venta y caja: lo que necesita la pantalla de venta. En el local sale de la API de
// Spring; en la web (contingencia, con su propia caja) son funciones de Supabase que se
// ejecutan completas o no se ejecutan (web_abrir_caja, web_cobrar, web_cerrar_caja).
import { metodosPagoApi, productosApi, tesoreriaApi, ventasApi } from '@/api/local'
import type { CierreCaja, EstadoCaja, MetodoPago, ProductoVenta, ResumenTesoreria, VentaCheckoutRespuesta } from '@/api/tipos'
import { esNube } from '@/modo'
import { supabase, verificar } from './supabase'

export interface CuerpoCobro {
  items: { barcode: string; quantity: number }[]
  discount: number
  payment: { method: string; amountReceived: number | null }
}

export interface VentaDatos {
  estadoCaja(): Promise<EstadoCaja>
  abrirCaja(monto: number, notas: string): Promise<unknown>
  cerrarCaja(billetes: { denomination: number; quantity: number }[], notas: string): Promise<CierreCaja>
  /** Totales de la caja abierta (para el cierre). */
  resumenCaja(): Promise<ResumenTesoreria>
  metodosPago(): Promise<MetodoPago[]>
  porCodigo(codigo: string): Promise<ProductoVenta>
  buscar(texto: string): Promise<ProductoVenta[]>
  cobrar(cuerpo: CuerpoCobro): Promise<VentaCheckoutRespuesta>
}

const local: VentaDatos = {
  estadoCaja: tesoreriaApi.estadoCaja,
  abrirCaja: tesoreriaApi.abrirCaja,
  cerrarCaja: tesoreriaApi.cerrarCaja,
  resumenCaja: tesoreriaApi.resumen,
  metodosPago: () => metodosPagoApi.listar(true),
  porCodigo: productosApi.porCodigo,
  buscar: (texto) => productosApi.buscar(texto),
  cobrar: ventasApi.cobrar,
}

// ---------------------------------------------------------------- nube

interface FilaProductoVenta {
  id: string
  codigo_barra: string
  nombre: string
  precio_venta: number
  categoria_id: string | null
  categorias: { nombre: string } | null
}

const CAMPOS_VENTA = 'id, codigo_barra, nombre, precio_venta, categoria_id, categorias!productos_categoria_id_fkey(nombre)'

function productoVenta(f: FilaProductoVenta): ProductoVenta {
  return {
    id: f.id,
    barcode: f.codigo_barra,
    name: f.nombre,
    price: Number(f.precio_venta),
    categoryId: f.categoria_id,
    categoryName: f.categorias?.nombre ?? null,
  }
}

/** Ejecuta una funcion de la nube y devuelve su resultado. */
async function funcion<T>(nombre: string, parametros?: Record<string, unknown>): Promise<T> {
  return verificar(await supabase().rpc(nombre, parametros)) as T
}

const nube: VentaDatos = {
  estadoCaja: () => funcion<EstadoCaja>('web_estado_caja'),
  abrirCaja: (monto, notas) => funcion('web_abrir_caja', { p_monto: monto, p_notas: notas || null }),
  cerrarCaja: (billetes, notas) => funcion<CierreCaja>('web_cerrar_caja', { p_billetes: billetes, p_notas: notas || null }),
  async resumenCaja() {
    const estado = await funcion<EstadoCaja>('web_estado_caja')
    const vacio = { incomes: 0, sales: 0, expenses: 0, cashInDrawer: 0, transfersDebited: 0 }
    if (!estado.open || !estado.id) return vacio
    const filas = verificar(await supabase()
      .from('movimientos_tesoreria')
      .select('tipo, monto, anulado, afecta_caja')
      .eq('caja_id', estado.id)) as { tipo: string; monto: number; anulado: boolean; afecta_caja: boolean }[]
    const r = { ...vacio }
    for (const m of filas) {
      if (m.anulado) continue
      const monto = Number(m.monto)
      if (m.tipo === 'INGRESO') r.incomes += Math.abs(monto)
      else if (m.tipo === 'EGRESO') r.expenses += Math.abs(monto)
      else if (m.tipo === 'VENTA') r.sales += Math.abs(monto)
      if (m.afecta_caja) r.cashInDrawer += monto
      else if (m.tipo === 'VENTA') r.transfersDebited += Math.abs(monto)
    }
    return r
  },
  async metodosPago() {
    const filas = verificar(await supabase()
      .from('metodos_pago')
      .select('id, codigo, nombre, afecta_caja, activo, orden')
      .eq('activo', true)
      .order('orden')) as { id: string; codigo: string; nombre: string; afecta_caja: boolean; activo: boolean; orden: number }[]
    return filas.map((m) => ({ id: m.id, code: m.codigo, name: m.nombre, affectsCash: m.afecta_caja, active: m.activo, order: m.orden }))
  },
  async porCodigo(codigo) {
    const filas = verificar(await supabase()
      .from('productos')
      .select(CAMPOS_VENTA)
      .eq('codigo_barra', codigo.trim())
      .eq('activo', true)
      .limit(1)) as unknown as FilaProductoVenta[]
    if (!filas.length) throw new Error(`Producto no encontrado: ${codigo}`)
    return productoVenta(filas[0])
  },
  async buscar(texto) {
    const t = texto.trim().replace(/[%,()]/g, ' ')
    const filas = verificar(await supabase()
      .from('productos')
      .select(CAMPOS_VENTA)
      .eq('activo', true)
      .or(`nombre.ilike.%${t}%,codigo_barra.ilike.%${t}%`)
      .order('nombre')
      .limit(20)) as unknown as FilaProductoVenta[]
    return filas.map(productoVenta)
  },
  cobrar: (c) => funcion<VentaCheckoutRespuesta>('web_cobrar', {
    p_items: c.items,
    p_descuento: c.discount,
    p_metodo: c.payment.method,
    p_recibido: c.payment.amountReceived,
  }),
}

export const ventaDatos: VentaDatos = esNube ? nube : local
