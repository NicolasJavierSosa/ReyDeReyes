// Descuento automatico por combos: si en el carrito estan los productos de un combo,
// se cobra el precio del combo. Misma logica que tenia la pantalla anterior.
import type { Producto, Uuid } from '@/api/tipos'
import { redondearDinero } from '@/utils/formato'

export interface LineaCarrito {
  productId: Uuid | null
  qty: number
}

export interface ComboAplicado {
  nombre: string
  veces: number
  precioCombo: number
  valorProductos: number
}

interface ComboItems {
  tipo: 'items'
  nombre: string
  precio: number
  cantidades: Map<Uuid, number>
  valorProductos: number
  ahorro: number
}

interface ComboGrupo {
  tipo: 'grupo'
  nombre: string
  precio: number
  categoriaId: Uuid
  cantidad: number
}

export function definirCombos(productos: Producto[]): (ComboItems | ComboGrupo)[] {
  const porId = new Map(productos.map((p) => [p.id, p]))
  const combos: (ComboItems | ComboGrupo)[] = []
  for (const p of productos) {
    if (!p.combo || !p.active) continue
    if (p.comboItems.length) {
      const cantidades = new Map<Uuid, number>()
      p.comboItems.forEach((id) => {
        const item = porId.get(id)
        if (item && !item.combo) cantidades.set(id, (cantidades.get(id) ?? 0) + 1)
      })
      if (!cantidades.size) continue
      let valor = 0
      cantidades.forEach((n, id) => { valor += (Number(porId.get(id)?.price) || 0) * n })
      combos.push({
        tipo: 'items',
        nombre: p.name,
        precio: Number(p.price) || 0,
        cantidades,
        valorProductos: redondearDinero(valor),
        ahorro: redondearDinero(Math.max(valor - (Number(p.price) || 0), 0)),
      })
    }
    if (p.comboGroupCategoryId && p.comboGroupQuantity && p.comboGroupQuantity > 0) {
      combos.push({ tipo: 'grupo', nombre: p.name, precio: Number(p.price) || 0, categoriaId: p.comboGroupCategoryId, cantidad: p.comboGroupQuantity })
    }
  }
  return combos
}

export function calcularDescuentoCombos(
  carrito: LineaCarrito[],
  productos: Producto[],
  combos: (ComboItems | ComboGrupo)[],
): { descuento: number; aplicados: ComboAplicado[] } {
  if (!combos.length || !carrito.length) return { descuento: 0, aplicados: [] }
  const porId = new Map(productos.map((p) => [p.id, p]))
  const disponible = new Map<Uuid, number>()
  carrito.forEach((l) => {
    if (!l.productId || porId.get(l.productId)?.combo || l.qty <= 0) return
    disponible.set(l.productId, (disponible.get(l.productId) ?? 0) + l.qty)
  })
  if (!disponible.size) return { descuento: 0, aplicados: [] }

  let descuento = 0
  const aplicados: ComboAplicado[] = []

  // 1. Combos de productos fijos, primero los que mas ahorran
  combos
    .filter((c): c is ComboItems => c.tipo === 'items' && c.ahorro > 0)
    .sort((a, b) => b.ahorro - a.ahorro)
    .forEach((c) => {
      let veces = Infinity
      c.cantidades.forEach((n, id) => { veces = Math.min(veces, Math.floor((disponible.get(id) ?? 0) / n)) })
      if (!Number.isFinite(veces) || veces <= 0) return
      descuento += c.ahorro * veces
      aplicados.push({ nombre: c.nombre, veces, precioCombo: c.precio * veces, valorProductos: c.valorProductos * veces })
      c.cantidades.forEach((n, id) => {
        const resto = (disponible.get(id) ?? 0) - n * veces
        if (resto <= 0) disponible.delete(id)
        else disponible.set(id, resto)
      })
    })

  // 2. Combos de grupo (N de una categoria), con lo que quedo
  let restantes: { categoriaId: Uuid | null; precio: number }[] = []
  disponible.forEach((qty, id) => {
    const p = porId.get(id)
    if (!p || p.combo) return
    for (let i = 0; i < qty; i++) restantes.push({ categoriaId: p.categoryId, precio: Number(p.price) || 0 })
  })
  combos
    .filter((c): c is ComboGrupo => c.tipo === 'grupo')
    .forEach((c) => {
      const candidatos = restantes.filter((r) => r.categoriaId === c.categoriaId).sort((a, b) => b.precio - a.precio)
      let usados: typeof restantes = []
      let valor = 0
      let veces = 0
      while (candidatos.length >= c.cantidad) {
        const grupo = candidatos.splice(0, c.cantidad)
        const suma = grupo.reduce((acc, r) => acc + r.precio, 0)
        if (suma <= c.precio) break
        veces++
        valor += suma
        usados = usados.concat(grupo)
      }
      if (veces > 0) {
        descuento += valor - c.precio * veces
        aplicados.push({ nombre: c.nombre, veces, precioCombo: c.precio * veces, valorProductos: valor })
        restantes = restantes.filter((r) => !usados.includes(r))
      }
    })

  return { descuento: redondearDinero(descuento), aplicados }
}
