// Catalogo (productos, combos, categorias). Mismas operaciones en el local (API de
// Spring) y en la web del duenio (Supabase directo, con reglas de acceso RLS).
import { categoriasApi, productosApi, proveedoresApi } from '@/api/local'
import type { Categoria, ComboRequest, Producto, ProductoRequest, Proveedor, Uuid } from '@/api/tipos'
import { esNube } from '@/modo'
import { supabase, verificar } from './supabase'

export interface CatalogoDatos {
  productos(): Promise<Producto[]>
  crearProducto(p: ProductoRequest): Promise<unknown>
  actualizarProducto(id: Uuid, p: ProductoRequest): Promise<unknown>
  crearCombo(c: ComboRequest): Promise<unknown>
  actualizarCombo(id: Uuid, c: ComboRequest): Promise<unknown>
  estadoProducto(id: Uuid, activo: boolean): Promise<unknown>
  categorias(): Promise<Categoria[]>
  crearCategoria(nombre: string): Promise<unknown>
  renombrarCategoria(id: Uuid, nombre: string): Promise<unknown>
  estadoCategoria(id: Uuid, activa: boolean): Promise<unknown>
  proveedores(): Promise<Proveedor[]>
}

const local: CatalogoDatos = {
  productos: productosApi.listar,
  crearProducto: productosApi.crear,
  actualizarProducto: productosApi.actualizar,
  crearCombo: productosApi.crearCombo,
  actualizarCombo: productosApi.actualizarCombo,
  estadoProducto: productosApi.cambiarEstado,
  categorias: () => categoriasApi.listar('all'),
  crearCategoria: categoriasApi.crear,
  renombrarCategoria: categoriasApi.actualizar,
  estadoCategoria: categoriasApi.cambiarEstado,
  proveedores: () => proveedoresApi.listar('all'),
}

// ---------------------------------------------------------------- nube

interface FilaProducto {
  id: Uuid
  codigo_barra: string
  nombre: string
  descripcion: string | null
  precio_costo: number
  precio_venta: number
  activo: boolean
  stock_minimo: number
  categoria_id: Uuid | null
  unidad_medida: string | null
  tipo_id: string | null
  proveedor_id: string | null
  combo: boolean
  combo_items: { productoId: Uuid; cantidad: number }[]
  combo_grupo_categoria_id: Uuid | null
  combo_grupo_cantidad: number | null
}

const UNIDADES: Record<string, string> = { UN: 'Unidad', KG: 'Kilogramo', G: 'Gramo', L: 'Litro', ML: 'Mililitro' }

async function asegurarUnidadYTipo(unidad: string, tipo: string): Promise<{ unidad: string; tipoId: string }> {
  const sb = supabase()
  const abreviatura = unidad.trim().toUpperCase()
  const nombreTipo = tipo.trim()
  const tipoId = nombreTipo.toLowerCase()
  verificar(await sb.from('unidades_medida').upsert(
    { abreviatura, nombre: UNIDADES[abreviatura] ?? abreviatura },
    { onConflict: 'abreviatura', ignoreDuplicates: true },
  ))
  verificar(await sb.from('tipos').upsert({ id: tipoId, nombre: nombreTipo }, { onConflict: 'id', ignoreDuplicates: true }))
  return { unidad: abreviatura, tipoId }
}

function camposComunes(p: { barcode: string; name: string; description: string; categoryId: Uuid | null; cost: number; price: number; active: boolean }) {
  return {
    codigo_barra: p.barcode.trim(),
    nombre: p.name.trim(),
    descripcion: p.description?.trim() || null,
    precio_costo: Number(p.cost) || 0,
    precio_venta: Number(p.price) || 0,
    activo: p.active,
    categoria_id: p.categoryId,
  }
}

function camposCombo(c: ComboRequest) {
  const grupo = c.groupCategoryId != null
  return {
    combo: true,
    combo_items: grupo ? [] : (c.items ?? []).map((productoId) => ({ productoId, cantidad: 1 })),
    combo_grupo_categoria_id: grupo ? c.groupCategoryId : null,
    combo_grupo_cantidad: grupo ? c.groupQuantity : null,
  }
}

const nube: CatalogoDatos = {
  async productos() {
    const sb = supabase()
    const [filas, categorias, proveedores, tipos, stock] = await Promise.all([
      sb.from('productos').select('*').order('nombre'),
      sb.from('categorias').select('id, nombre'),
      sb.from('proveedores').select('telefono, nombre'),
      sb.from('tipos').select('id, nombre'),
      sb.from('v_stock').select('producto_id, cantidad'),
    ])
    const cat = new Map(verificar(categorias).map((c: { id: string; nombre: string }) => [c.id, c.nombre]))
    const prov = new Map(verificar(proveedores).map((p: { telefono: string; nombre: string }) => [p.telefono, p.nombre]))
    const tip = new Map(verificar(tipos).map((t: { id: string; nombre: string }) => [t.id, t.nombre]))
    const stk = new Map(verificar(stock).map((s: { producto_id: string; cantidad: number }) => [s.producto_id, Number(s.cantidad)]))
    return (verificar(filas) as FilaProducto[]).map((f) => ({
      id: f.id,
      barcode: f.codigo_barra,
      name: f.nombre,
      description: f.descripcion,
      categoryId: f.categoria_id,
      categoryName: f.categoria_id ? cat.get(f.categoria_id) ?? null : null,
      supplierId: f.proveedor_id,
      supplierName: f.proveedor_id ? prov.get(f.proveedor_id) ?? null : null,
      unit: f.unidad_medida,
      type: f.tipo_id ? tip.get(f.tipo_id) ?? f.tipo_id : null,
      cost: Number(f.precio_costo),
      price: Number(f.precio_venta),
      // El stock lo maneja el local; los combos no tienen stock propio
      stock: f.combo ? 0 : stk.get(f.id) ?? 0,
      minStock: Number(f.stock_minimo),
      active: f.activo,
      combo: f.combo,
      comboItems: (f.combo_items ?? []).map((i) => i.productoId),
      comboGroupCategoryId: f.combo_grupo_categoria_id,
      comboGroupCategoryName: f.combo_grupo_categoria_id ? cat.get(f.combo_grupo_categoria_id) ?? null : null,
      comboGroupQuantity: f.combo_grupo_cantidad,
    }))
  },
  async crearProducto(p) {
    const { unidad, tipoId } = await asegurarUnidadYTipo(p.unit, p.type)
    return verificar(await supabase().from('productos').insert({
      ...camposComunes(p),
      unidad_medida: unidad,
      tipo_id: tipoId,
      proveedor_id: p.supplierId,
      stock_minimo: Number(p.minStock) || 0,
    }))
  },
  async actualizarProducto(id, p) {
    const { unidad, tipoId } = await asegurarUnidadYTipo(p.unit, p.type)
    return verificar(await supabase().from('productos').update({
      ...camposComunes(p),
      unidad_medida: unidad,
      tipo_id: tipoId,
      proveedor_id: p.supplierId,
      stock_minimo: Number(p.minStock) || 0,
    }).eq('id', id))
  },
  async crearCombo(c) {
    const { unidad, tipoId } = await asegurarUnidadYTipo(c.unit, c.type)
    const barcode = c.barcode.trim() || `COMBO-${crypto.randomUUID()}`
    return verificar(await supabase().from('productos').insert({
      ...camposComunes({ ...c, barcode }),
      ...camposCombo(c),
      unidad_medida: unidad,
      tipo_id: tipoId,
    }))
  },
  async actualizarCombo(id, c) {
    const { unidad, tipoId } = await asegurarUnidadYTipo(c.unit, c.type)
    const datos: Record<string, unknown> = { ...camposComunes(c), ...camposCombo(c), unidad_medida: unidad, tipo_id: tipoId }
    if (!c.barcode.trim()) delete datos.codigo_barra
    return verificar(await supabase().from('productos').update(datos).eq('id', id))
  },
  async estadoProducto(id, activo) {
    return verificar(await supabase().from('productos').update({ activo }).eq('id', id))
  },
  async categorias() {
    const filas = verificar(await supabase().from('categorias').select('id, nombre, activa').order('nombre'))
    return filas as Categoria[]
  },
  async crearCategoria(nombre) {
    return verificar(await supabase().from('categorias').insert({ nombre: nombre.trim() }))
  },
  async renombrarCategoria(id, nombre) {
    return verificar(await supabase().from('categorias').update({ nombre: nombre.trim() }).eq('id', id))
  },
  async estadoCategoria(id, activa) {
    return verificar(await supabase().from('categorias').update({ activa }).eq('id', id))
  },
  async proveedores() {
    const filas = verificar(await supabase().from('proveedores').select('telefono, nombre, activo').order('nombre'))
    return (filas as { telefono: string; nombre: string; activo: boolean }[])
      .map((p) => ({ phone: p.telefono, name: p.nombre, active: p.activo }))
  },
}

export const catalogo: CatalogoDatos = esNube ? nube : local
