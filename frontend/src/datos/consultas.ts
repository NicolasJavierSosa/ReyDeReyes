// Consultas de solo lectura de ventas, cajas y tesoreria. En el local salen de la API
// de Spring; en la web del duenio, de Supabase.
import { tesoreriaApi, ventasApi } from '@/api/local'
import type { MovimientoTesoreria, Uuid, VentaDetalle, VentaResumen } from '@/api/tipos'
import { esNube } from '@/modo'
import { supabase, verificar } from './supabase'

export interface CajaResumen {
  id: Uuid
  open: boolean
  openedAt: string | null
  closedAt: string | null
  baseAmount: number
  expectedCash: number | null
  countedCash: number | null
  difference: number | null
  notes: string | null
}

export interface ConsultasDatos {
  ventas(desde: string, hasta: string): Promise<VentaResumen[]>
  ventaDetalle(id: Uuid): Promise<VentaDetalle>
  /** Cajas (la abierta primero), con fechas de cierre entre desde y hasta. */
  cajas(desde: string | null, hasta: string | null, pagina: number): Promise<{ items: CajaResumen[]; hayMas: boolean }>
  movimientosDeCaja(id: Uuid): Promise<MovimientoTesoreria[]>
}

const local: ConsultasDatos = {
  ventas: ventasApi.listar,
  ventaDetalle: ventasApi.detalle,
  async cajas(desde, hasta, pagina) {
    const [abierta, cerradas] = await Promise.all([
      pagina === 0 ? tesoreriaApi.estadoCaja() : Promise.resolve(null),
      tesoreriaApi.cajasCerradas(desde, hasta, pagina, 20),
    ])
    const items: CajaResumen[] = []
    if (abierta?.open && abierta.id) {
      items.push({
        id: abierta.id, open: true, openedAt: abierta.openedAt, closedAt: null, baseAmount: abierta.baseAmount,
        expectedCash: null, countedCash: null, difference: null, notes: null,
      })
    }
    cerradas.items.forEach((c) => items.push({
      id: c.id, open: false, openedAt: null, closedAt: c.closedAt, baseAmount: 0,
      expectedCash: c.expectedCash, countedCash: c.countedCash, difference: c.difference, notes: c.notes,
    }))
    return { items, hayMas: cerradas.hasMore }
  },
  movimientosDeCaja: tesoreriaApi.movimientosDeCaja,
}

// ---------------------------------------------------------------- nube

const finDelDia = (dia: string) => {
  const d = new Date(`${dia}T00:00:00`)
  d.setDate(d.getDate() + 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}T00:00:00`
}

interface FilaVenta {
  id: Uuid
  numero: number
  fecha_hora: string
  subtotal: number
  descuento_total: number
  total: number
  estado: 'COMPLETADA' | 'ANULADA'
  pagos: { metodo_nombre: string }[]
}

const nube: ConsultasDatos = {
  async ventas(desde, hasta) {
    const filas = verificar(await supabase()
      .from('ventas')
      .select('id, numero, fecha_hora, total, estado, pagos(metodo_nombre), detalles_venta(count)')
      .gte('fecha_hora', `${desde}T00:00:00`)
      .lt('fecha_hora', finDelDia(hasta))
      .order('fecha_hora', { ascending: false })
      .limit(1000)) as (FilaVenta & { detalles_venta: { count: number }[] })[]
    return filas.map((v) => ({
      id: v.id,
      ticket: String(v.numero).padStart(6, '0'),
      dateTime: v.fecha_hora,
      total: Number(v.total),
      status: v.estado,
      paymentMethod: v.pagos?.[0]?.metodo_nombre ?? null,
      items: v.detalles_venta?.[0]?.count ?? 0,
    }))
  },
  async ventaDetalle(id) {
    const v = verificar(await supabase()
      .from('ventas')
      .select('id, numero, fecha_hora, subtotal, descuento_total, total, estado, pagos(metodo_nombre), '
        + 'detalles_venta(cantidad, precio_unitario, subtotal, total_linea, productos(codigo_barra, nombre))')
      .eq('id', id)
      .single()) as FilaVenta & {
        detalles_venta: { cantidad: number; precio_unitario: number; subtotal: number; total_linea: number; productos: { codigo_barra: string; nombre: string } }[]
      }
    return {
      id: v.id,
      ticket: String(v.numero).padStart(6, '0'),
      dateTime: v.fecha_hora,
      subtotal: Number(v.subtotal),
      discount: Number(v.descuento_total),
      total: Number(v.total),
      status: v.estado,
      paymentMethod: v.pagos?.[0]?.metodo_nombre ?? null,
      items: v.detalles_venta.map((d) => ({
        barcode: d.productos?.codigo_barra ?? '',
        name: d.productos?.nombre ?? '',
        quantity: Number(d.cantidad),
        unitPrice: Number(d.precio_unitario),
        subtotal: Number(d.subtotal),
        totalLine: Number(d.total_linea),
      })),
    }
  },
  async cajas(desde, hasta, pagina) {
    let consulta = supabase()
      .from('cajas')
      .select('id, estado, fecha_apertura, fecha_cierre, monto_apertura, efectivo_esperado, efectivo_contado, diferencia, observaciones')
      .order('fecha_apertura', { ascending: false })
      .range(pagina * 20, pagina * 20 + 20)
    if (desde) consulta = consulta.gte('fecha_apertura', `${desde}T00:00:00`)
    if (hasta) consulta = consulta.lt('fecha_apertura', finDelDia(hasta))
    const filas = verificar(await consulta) as {
      id: Uuid; estado: string; fecha_apertura: string; fecha_cierre: string | null; monto_apertura: number
      efectivo_esperado: number | null; efectivo_contado: number | null; diferencia: number | null; observaciones: string | null
    }[]
    const items = filas.slice(0, 20).map((c) => ({
      id: c.id,
      open: c.estado === 'ABIERTA',
      openedAt: c.fecha_apertura,
      closedAt: c.fecha_cierre,
      baseAmount: Number(c.monto_apertura),
      expectedCash: c.efectivo_esperado,
      countedCash: c.efectivo_contado,
      difference: c.diferencia,
      notes: c.observaciones,
    }))
    return { items, hayMas: filas.length > 20 }
  },
  async movimientosDeCaja(id) {
    const filas = verificar(await supabase()
      .from('movimientos_tesoreria')
      .select('id, fecha_hora, tipo, concepto, detalle, usuario, monto, metodo_nombre, venta_id, afecta_caja, anulado')
      .eq('caja_id', id)
      .order('fecha_hora', { ascending: false })) as {
        id: Uuid; fecha_hora: string; tipo: 'INGRESO' | 'EGRESO' | 'VENTA'; concepto: string; detalle: string | null
        usuario: string | null; monto: number; metodo_nombre: string | null; venta_id: Uuid | null; afecta_caja: boolean; anulado: boolean
      }[]
    return filas.map((m) => ({
      id: m.id,
      dateTime: m.fecha_hora,
      type: m.tipo,
      concept: m.concepto,
      detail: m.detalle,
      user: m.usuario,
      amount: Number(m.monto),
      paymentMethod: m.metodo_nombre,
      saleId: m.venta_id,
      affectsCash: m.afecta_caja,
      deletable: false,
      voided: m.anulado,
    }))
  },
}

export const consultas: ConsultasDatos = esNube ? nube : local
