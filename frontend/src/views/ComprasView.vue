<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Modal from '@/components/Modal.vue'
import { comprasApi, productosApi, proveedoresApi } from '@/api/local'
import type { Producto, Proveedor, SolicitudCompra } from '@/api/tipos'
import { useAvisos } from '@/stores/avisos'
import { cantidad, normalizar } from '@/utils/formato'

const avisos = useAvisos()
const proveedores = ref<Proveedor[]>([])
const productos = ref<Producto[]>([])
const pendientes = ref<SolicitudCompra[]>([])
const texto = ref('')

async function cargar() {
  try {
    const [pr, p, s] = await Promise.all([proveedoresApi.listar('all'), productosApi.listar(), comprasApi.listar('pending')])
    proveedores.value = pr
    productos.value = p
    pendientes.value = s
  } catch (e) {
    avisos.error(e)
  }
}
onMounted(cargar)

const productosDe = (p: Proveedor) => productos.value.filter((x) => x.supplierId === p.phone && !x.combo && x.active)
const alertasDe = (p: Proveedor) => productosDe(p).filter((x) => x.stock <= x.minStock).length

const visibles = computed(() => {
  const t = normalizar(texto.value)
  return proveedores.value
    .filter((p) => !t || normalizar(`${p.name} ${p.phone}`).includes(t))
    .sort((a, b) => Number(b.active) - Number(a.active) || alertasDe(b) - alertasDe(a) || a.name.localeCompare(b.name))
})

// ------------------------------------------------------------------ proveedor

const editandoProv = ref<{ original: string | null; phone: string; name: string } | null>(null)

async function guardarProveedor() {
  const e = editandoProv.value!
  try {
    if (e.original) await proveedoresApi.actualizar(e.original, { phone: e.phone, name: e.name })
    else await proveedoresApi.crear({ phone: e.phone, name: e.name, active: true })
    avisos.exito('Proveedor guardado')
    editandoProv.value = null
    await cargar()
  } catch (err) {
    avisos.error(err)
  }
}

async function cambiarEstadoProveedor(p: Proveedor) {
  try {
    await proveedoresApi.cambiarEstado(p.phone, !p.active)
    await cargar()
  } catch (e) {
    avisos.error(e)
  }
}

// ------------------------------------------------------------------ pedido

interface LineaPedido { producto: Producto; elegido: boolean; qty: number }
const pedidoA = ref<Proveedor | null>(null)
const lineas = ref<LineaPedido[]>([])
const mensaje = ref('')

function abrirPedido(p: Proveedor) {
  pedidoA.value = p
  mensaje.value = ''
  lineas.value = productosDe(p).map((producto) => {
    const critico = producto.stock <= producto.minStock
    const sugerido = Math.max(Math.ceil(producto.minStock * 2 - producto.stock), 1)
    return { producto, elegido: critico, qty: sugerido }
  }).sort((a, b) => Number(b.elegido) - Number(a.elegido) || a.producto.name.localeCompare(b.producto.name))
}

function telefonoWhatsApp(telefono: string) {
  let d = telefono.replace(/\D/g, '')
  if (d.startsWith('00')) d = d.substring(2)
  if (d.startsWith('0') && d.length > 10) d = d.substring(1)
  if (d.length === 11 && d.startsWith('15')) d = d.substring(2)
  if (d.length === 10) return `549${d}`
  return d
}

async function enviarPedido() {
  const p = pedidoA.value!
  const items = lineas.value.filter((l) => l.elegido && l.qty > 0)
  if (!items.length) return avisos.mostrar('Elegí al menos un producto', 'error')
  const texto = `Hola ${p.name},\nTe escribo desde Despensa Rey de Reyes para hacerte un pedido:\n\n`
    + items.map((l) => `- ${cantidad(l.qty)}x ${l.producto.name}`).join('\n')
    + '\n\nQuedo a la espera de confirmacion y total. Gracias!'
  try {
    await comprasApi.crear(p.phone, items.map((l) => ({ productId: l.producto.id, qty: l.qty })))
    avisos.exito('Pedido registrado')
    await cargar()
  } catch (e) {
    return avisos.error(e)
  }
  const telefono = telefonoWhatsApp(p.phone)
  const ventana = telefono ? window.open(`https://wa.me/${telefono}?text=${encodeURIComponent(texto)}`, '_blank') : null
  if (ventana) pedidoA.value = null
  else mensaje.value = texto // sin internet o bloqueado: se muestra para copiar
}

async function copiarMensaje() {
  try {
    await navigator.clipboard.writeText(mensaje.value)
    avisos.exito('Mensaje copiado')
  } catch {
    avisos.mostrar('No se pudo copiar; seleccionalo y copialo a mano', 'info')
  }
}

// ------------------------------------------------------------------ recepcion

const recibiendo = ref<SolicitudCompra | null>(null)
const recibido = ref<Record<string, number>>({})

function abrirRecepcion(s: SolicitudCompra) {
  recibiendo.value = s
  recibido.value = Object.fromEntries(s.items.map((i) => [i.id, Number(i.qtyRequested)]))
}

async function confirmarRecepcion() {
  const s = recibiendo.value!
  try {
    const r = await comprasApi.recepcionar(s.id, s.items.map((i) => ({ itemId: i.id, receivedQty: Number(recibido.value[i.id]) || 0 })))
    avisos.exito(r.status === 'RECIBIDA' ? 'Pedido recibido completo. Stock actualizado.' : 'Recepcion parcial registrada. Stock actualizado.')
    recibiendo.value = null
    await cargar()
  } catch (e) {
    avisos.error(e)
  }
}
</script>

<template>
  <div class="p-4 lg:p-6 max-w-7xl mx-auto space-y-4">
    <div class="flex items-center gap-2">
      <h1 class="text-2xl font-bold mr-auto">Compras</h1>
      <button class="btn-primario" @click="editandoProv = { original: null, phone: '', name: '' }"><i class="fa-solid fa-plus"></i> Nuevo proveedor</button>
    </div>

    <section v-if="pendientes.length" class="tarjeta">
      <h2 class="font-semibold px-4 py-3 border-b border-slate-200"><i class="fa-solid fa-truck-ramp-box text-amber-500 mr-2"></i>Pedidos pendientes de recibir</h2>
      <ul class="divide-y">
        <li v-for="s in pendientes" :key="s.id" class="px-4 py-3 flex flex-wrap items-center gap-3">
          <div class="mr-auto">
            <div class="font-medium">{{ s.supplierName }}</div>
            <div class="text-xs text-slate-500">{{ s.dateTime }} · {{ s.items.length }} productos</div>
          </div>
          <span :class="['insignia', s.status === 'PARCIAL' ? 'bg-amber-100 text-amber-700' : 'bg-slate-200 text-slate-600']">{{ s.status === 'PARCIAL' ? 'Parcial' : 'Solicitado' }}</span>
          <button class="btn-secundario !py-1" @click="abrirRecepcion(s)"><i class="fa-solid fa-dolly"></i> Recibir</button>
        </li>
      </ul>
    </section>

    <div class="tarjeta p-3"><input v-model="texto" class="campo max-w-sm" placeholder="Buscar proveedor..." aria-label="Buscar proveedor" /></div>
    <div class="tarjeta overflow-x-auto">
      <table class="tabla w-full">
        <thead><tr><th>Proveedor</th><th>WhatsApp</th><th>Productos</th><th>Stock bajo</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!visibles.length"><td colspan="5" class="text-center !py-6 text-slate-400">No hay proveedores</td></tr>
          <tr v-for="p in visibles" :key="p.phone" :class="{ 'opacity-50': !p.active }">
            <td class="font-semibold">{{ p.name }} <span v-if="!p.active" class="insignia bg-slate-200 text-slate-600">Baja</span></td>
            <td><i class="fa-brands fa-whatsapp text-emerald-500 mr-1"></i>{{ p.phone }}</td>
            <td>{{ productosDe(p).length }}</td>
            <td><span v-if="alertasDe(p)" class="insignia bg-rose-100 text-rose-700">{{ alertasDe(p) }} con stock bajo</span><span v-else class="text-slate-400">-</span></td>
            <td class="text-right whitespace-nowrap">
              <button v-if="p.active" class="btn-primario !py-1" @click="abrirPedido(p)"><i class="fa-solid fa-cart-flatbed"></i> Pedir</button>
              <button class="p-1.5 text-slate-400 hover:text-emerald-600" title="Editar" @click="editandoProv = { original: p.phone, phone: p.phone, name: p.name }"><i class="fa-solid fa-pen"></i></button>
              <button class="p-1.5 text-slate-400 hover:text-rose-600" :title="p.active ? 'Dar de baja' : 'Reactivar'" @click="cambiarEstadoProveedor(p)"><i :class="['fa-solid', p.active ? 'fa-ban' : 'fa-rotate-left']"></i></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal v-if="editandoProv" :titulo="editandoProv.original ? 'Modificar proveedor' : 'Nuevo proveedor'" @cerrar="editandoProv = null">
      <form id="form-prov" class="space-y-3" @submit.prevent="guardarProveedor">
        <div><label class="etiqueta" for="pr-nombre">Empresa / distribuidor *</label><input id="pr-nombre" v-model="editandoProv.name" class="campo" required autofocus /></div>
        <div>
          <label class="etiqueta" for="pr-tel">Telefono (WhatsApp) *</label>
          <input id="pr-tel" v-model="editandoProv.phone" class="campo" required :disabled="!!editandoProv.original" placeholder="Ej: 11 2345 6789" />
          <p v-if="editandoProv.original" class="text-xs text-slate-500 mt-1">El telefono identifica al proveedor y no se puede cambiar.</p>
        </div>
      </form>
      <template #pie>
        <button class="btn-secundario" @click="editandoProv = null">Cancelar</button>
        <button class="btn-primario" form="form-prov">Guardar</button>
      </template>
    </Modal>

    <Modal v-if="pedidoA" :titulo="`Pedido a ${pedidoA.name}`" ancho="max-w-3xl" @cerrar="pedidoA = null">
      <div v-if="mensaje" class="space-y-3">
        <p class="text-sm text-slate-600">No se pudo abrir WhatsApp. Copiá el mensaje y envialo a mano:</p>
        <textarea v-model="mensaje" rows="10" class="campo font-mono text-sm" readonly></textarea>
      </div>
      <table v-else class="tabla w-full">
        <thead><tr><th></th><th>Producto</th><th class="!text-right">Stock</th><th class="!text-right">Minimo</th><th class="!text-right">A pedir</th></tr></thead>
        <tbody>
          <tr v-if="!lineas.length"><td colspan="5" class="text-center text-slate-400">Este proveedor no tiene productos asignados</td></tr>
          <tr v-for="l in lineas" :key="l.producto.id">
            <td><input v-model="l.elegido" type="checkbox" class="accent-emerald-600 w-4 h-4" :aria-label="`Pedir ${l.producto.name}`" /></td>
            <td>{{ l.producto.name }}</td>
            <td :class="['text-right', l.producto.stock <= l.producto.minStock ? 'text-rose-600 font-bold' : '']">{{ cantidad(l.producto.stock) }}</td>
            <td class="text-right">{{ cantidad(l.producto.minStock) }}</td>
            <td class="text-right"><input v-model.number="l.qty" type="number" min="0.001" step="any" class="campo !py-1 w-24 text-right" :aria-label="`Cantidad de ${l.producto.name}`" /></td>
          </tr>
        </tbody>
      </table>
      <template #pie>
        <template v-if="mensaje">
          <button class="btn-secundario" @click="copiarMensaje"><i class="fa-regular fa-copy"></i> Copiar</button>
          <button class="btn-primario" @click="pedidoA = null">Listo</button>
        </template>
        <template v-else>
          <button class="btn-secundario" @click="pedidoA = null">Cancelar</button>
          <button class="btn-primario" @click="enviarPedido"><i class="fa-brands fa-whatsapp"></i> Registrar y enviar por WhatsApp</button>
        </template>
      </template>
    </Modal>

    <Modal v-if="recibiendo" :titulo="`Recibir pedido de ${recibiendo.supplierName}`" ancho="max-w-2xl" @cerrar="recibiendo = null">
      <p class="text-sm text-slate-600 mb-3">Indicá lo recibido en total. Se suma al stock solo la diferencia con lo ya recibido.</p>
      <table class="tabla w-full">
        <thead><tr><th>Producto</th><th class="!text-right">Pedido</th><th class="!text-right">Ya recibido</th><th class="!text-right">Recibido total</th></tr></thead>
        <tbody>
          <tr v-for="i in recibiendo.items" :key="i.id">
            <td>{{ i.productName }}</td>
            <td class="text-right">{{ cantidad(i.qtyRequested) }}</td>
            <td class="text-right">{{ cantidad(i.qtyReceived) }}</td>
            <td class="text-right"><input v-model.number="recibido[i.id]" type="number" min="0" step="any" class="campo !py-1 w-24 text-right" :aria-label="`Recibido de ${i.productName}`" /></td>
          </tr>
        </tbody>
      </table>
      <template #pie>
        <button class="btn-secundario" @click="recibiendo = null">Cancelar</button>
        <button class="btn-primario" @click="confirmarRecepcion"><i class="fa-solid fa-check"></i> Confirmar recepcion</button>
      </template>
    </Modal>
  </div>
</template>
