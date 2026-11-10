<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Modal from '@/components/Modal.vue'
import AperturaCaja from '@/components/AperturaCaja.vue'
import DetalleVenta from '@/components/DetalleVenta.vue'
import { categoriasApi, metodosPagoApi, productosApi, ventasApi } from '@/api/local'
import type { MetodoPago, Producto, ProductoVenta, Uuid, VentaCheckoutRespuesta } from '@/api/tipos'
import { calcularDescuentoCombos, definirCombos } from '@/composables/combos'
import { useCaja } from '@/stores/caja'
import { useAvisos } from '@/stores/avisos'
import { useSync } from '@/stores/sync'
import { cantidad, dinero, normalizar, redondearCantidad, redondearDinero } from '@/utils/formato'

interface Linea {
  clave: number
  productId: Uuid | null
  codigo: string
  nombre: string
  precio: number
  qty: number
  /** Ventas por monto (fiambreria, verduleria): cantidad real enviada y total fijo */
  qtyVenta: number | null
  subtotalFijo: number | null
}

const caja = useCaja()
const avisos = useAvisos()
const sync = useSync()

const productos = ref<Producto[]>([])
const metodos = ref<MetodoPago[]>([])
const carrito = ref<Linea[]>([])
let claveLinea = 1

const codigo = ref('')
const cantidadIngreso = ref(1)
const busqueda = ref('')
const resultados = ref<ProductoVenta[]>([])
const inputCodigo = ref<HTMLInputElement | null>(null)

const ultimaVenta = ref<VentaCheckoutRespuesta | null>(null)
const verVentaId = ref<Uuid | null>(null)

// ------------------------------------------------------------------ carga

async function cargarProductos() {
  try {
    productos.value = await productosApi.listar()
  } catch (e) {
    avisos.error(e)
  }
}

onMounted(async () => {
  await Promise.all([caja.refrescar(), cargarProductos(), metodosPagoApi.listar(true).then((m) => (metodos.value = m)).catch(avisos.error)])
  enfocarCodigo()
  window.addEventListener('keydown', teclaGlobal)
})
onBeforeUnmount(() => window.removeEventListener('keydown', teclaGlobal))

function enfocarCodigo() {
  nextTick(() => inputCodigo.value?.focus())
}

// ------------------------------------------------------------------ carrito

const combos = computed(() => definirCombos(productos.value))
const subtotal = computed(() => redondearDinero(carrito.value.reduce((acc, l) => acc + subtotalLinea(l), 0)))
const descuentoCombos = computed(() =>
  calcularDescuentoCombos(carrito.value.map((l) => ({ productId: l.productId, qty: l.qty })), productos.value, combos.value),
)
const descuento = computed(() => Math.min(descuentoCombos.value.descuento, subtotal.value))
const total = computed(() => redondearDinero(Math.max(subtotal.value - descuento.value, 0)))
const unidades = computed(() => carrito.value.reduce((acc, l) => acc + (l.qtyVenta !== null ? 1 : l.qty), 0))

function subtotalLinea(l: Linea) {
  return l.subtotalFijo ?? redondearDinero(l.precio * l.qty)
}

function agregar(p: { id: Uuid | null; barcode: string; name: string; price: number }, qty: number) {
  const existente = carrito.value.find((l) => l.codigo === p.barcode && l.qtyVenta === null)
  if (existente) {
    existente.qty = redondearCantidad(existente.qty + qty)
    carrito.value = [existente, ...carrito.value.filter((l) => l !== existente)]
  } else {
    carrito.value.unshift({ clave: claveLinea++, productId: p.id, codigo: p.barcode, nombre: p.name, precio: Number(p.price) || 0, qty, qtyVenta: null, subtotalFijo: null })
  }
}

async function agregarPorCodigo() {
  const texto = codigo.value.trim()
  if (!texto) return
  if (!caja.estado.open) return avisos.mostrar('Abrí la caja para vender', 'error')
  const qty = Number(cantidadIngreso.value) > 0 ? Number(cantidadIngreso.value) : 1
  try {
    const p = await productosApi.porCodigo(texto)
    agregar(p, qty)
    codigo.value = ''
    cantidadIngreso.value = 1
  } catch {
    avisos.mostrar(`Producto no encontrado o inactivo: ${texto}`, 'error')
    inputCodigo.value?.select()
    return
  }
  enfocarCodigo()
}

let temporizadorBusqueda: number | undefined
watch(busqueda, (texto) => {
  window.clearTimeout(temporizadorBusqueda)
  if (texto.trim().length < 2) {
    resultados.value = []
    return
  }
  temporizadorBusqueda = window.setTimeout(async () => {
    try {
      resultados.value = await productosApi.buscar(texto.trim())
    } catch {
      resultados.value = []
    }
  }, 250)
})

function agregarDesdeBusqueda(p: ProductoVenta) {
  if (!caja.estado.open) return avisos.mostrar('Abrí la caja para vender', 'error')
  agregar(p, Number(cantidadIngreso.value) > 0 ? Number(cantidadIngreso.value) : 1)
  busqueda.value = ''
  resultados.value = []
  cantidadIngreso.value = 1
  enfocarCodigo()
}

function cambiarCantidad(l: Linea, delta: number) {
  l.qty = redondearCantidad(Math.max(l.qty + delta, 0))
  if (l.qty <= 0) quitar(l)
}

function quitar(l: Linea) {
  carrito.value = carrito.value.filter((x) => x !== l)
  enfocarCodigo()
}

function cancelarVenta() {
  if (!carrito.value.length || !confirm('¿Cancelar la venta actual?')) return
  carrito.value = []
  enfocarCodigo()
}

// ------------------------------------------------------------------ venta por monto

type Rubro = 'fiambreria' | 'carniceria' | 'verduleria'
const rubros: Record<Rubro, { titulo: string; icono: string; color: string; categoria: string; prefijo: string }> = {
  fiambreria: { titulo: 'Fiambreria', icono: 'fa-cheese', color: 'text-amber-600', categoria: 'Fiambreria', prefijo: 'FIAM' },
  carniceria: { titulo: 'Carniceria', icono: 'fa-drumstick-bite', color: 'text-red-600', categoria: 'Carniceria', prefijo: 'CARN' },
  verduleria: { titulo: 'Verduleria', icono: 'fa-carrot', color: 'text-lime-600', categoria: 'Verduleria', prefijo: 'VERD' },
}
const rubro = ref<Rubro | null>(null)
const rubroProductoId = ref<Uuid | ''>('')
const rubroMonto = ref<number | null>(null)
const rubroNuevo = ref({ visible: false, nombre: '', precio: null as number | null })

const productosDelRubro = computed(() => {
  if (!rubro.value) return []
  const clave = normalizar(rubros[rubro.value].categoria).slice(0, 6)
  return productos.value
    .filter((p) => !p.combo && p.active && normalizar(p.categoryName).includes(clave))
    .sort((a, b) => a.name.localeCompare(b.name))
})

function abrirRubro(r: Rubro) {
  if (!caja.estado.open) return avisos.mostrar('Abrí la caja para vender', 'error')
  rubro.value = r
  rubroMonto.value = null
  rubroNuevo.value = { visible: false, nombre: '', precio: null }
  rubroProductoId.value = productosDelRubro.value[0]?.id ?? ''
}

async function asegurarCategoria(nombre: string) {
  const categorias = await categoriasApi.listar('all')
  const existente = categorias.find((c) => normalizar(c.nombre).includes(normalizar(nombre).slice(0, 6)))
  if (existente) {
    if (!existente.activa) await categoriasApi.cambiarEstado(existente.id, true)
    return existente
  }
  return categoriasApi.crear(nombre)
}

/** Productos "a granel": se venden por monto, sin control de stock real. */
async function crearProductoDelRubro(nombre: string, precio: number) {
  const r = rubros[rubro.value!]
  const categoria = await asegurarCategoria(r.categoria)
  const creado = await productosApi.crear({
    barcode: `${r.prefijo}-${Date.now()}`, name: nombre, description: '', categoryId: categoria.id, supplierId: null,
    unit: 'KG', type: 'Producto', cost: 0, price: precio, stock: 999999, stockOriginal: null, minStock: 0, active: true,
  })
  await cargarProductos()
  return creado
}

async function guardarNuevoDelRubro() {
  const { nombre, precio } = rubroNuevo.value
  if (!nombre.trim() || !precio || precio <= 0) return avisos.mostrar('Complete nombre y precio', 'error')
  try {
    const creado = await crearProductoDelRubro(nombre.trim(), precio)
    rubroProductoId.value = creado.id
    rubroNuevo.value = { visible: false, nombre: '', precio: null }
    avisos.exito('Producto registrado')
  } catch (e) {
    avisos.error(e)
  }
}

async function agregarPorMonto() {
  const monto = Number(rubroMonto.value)
  if (!monto || monto <= 0) return avisos.mostrar('Ingrese un monto valido', 'error')
  try {
    let producto = rubro.value === 'verduleria' ? productosDelRubro.value[0] : productos.value.find((p) => p.id === rubroProductoId.value)
    if (!producto && rubro.value === 'verduleria') {
      await crearProductoDelRubro('Verduleria', 1)
      producto = productosDelRubro.value[0]
    }
    if (!producto) return avisos.mostrar('Seleccione un producto', 'error')
    if (!(producto.price > 0)) return avisos.mostrar('El producto no tiene precio de venta', 'error')
    carrito.value.unshift({
      clave: claveLinea++, productId: producto.id, codigo: producto.barcode, nombre: producto.name, precio: monto,
      qty: 1, qtyVenta: redondearCantidad(monto / producto.price), subtotalFijo: redondearDinero(monto),
    })
    rubro.value = null
    enfocarCodigo()
  } catch (e) {
    avisos.error(e)
  }
}

// ------------------------------------------------------------------ cobro

const cobrando = ref(false)
const metodoElegido = ref('EFECTIVO')
const entregado = ref<number | null>(null)
const enviando = ref(false)
const errorCobro = ref('')

const metodo = computed(() => metodos.value.find((m) => m.code === metodoElegido.value))
const vuelto = computed(() => Math.max(redondearDinero((Number(entregado.value) || 0) - total.value), 0))
const billetesRapidos = computed(() => {
  const t = total.value
  const opciones = new Set<number>([t])
  ;[1000, 2000, 10000, 20000].forEach((b) => opciones.add(Math.ceil(t / b) * b))
  return [...opciones].filter((v) => v >= t).sort((a, b) => a - b).slice(0, 5)
})

function abrirCobro() {
  if (!caja.estado.open) return avisos.mostrar('Abrí la caja para vender', 'error')
  if (!carrito.value.length || total.value <= 0) return avisos.mostrar('No hay productos para cobrar', 'error')
  if (!metodos.value.some((m) => m.code === metodoElegido.value)) metodoElegido.value = metodos.value[0]?.code ?? 'EFECTIVO'
  entregado.value = null
  errorCobro.value = ''
  cobrando.value = true
}

async function confirmarCobro() {
  errorCobro.value = ''
  const efectivo = !!metodo.value?.affectsCash
  if (efectivo && (Number(entregado.value) || 0) < total.value) {
    errorCobro.value = 'El monto entregado es menor al total'
    return
  }
  enviando.value = true
  try {
    const respuesta = await ventasApi.cobrar({
      items: carrito.value.map((l) => ({ barcode: l.codigo, quantity: l.qtyVenta ?? l.qty })),
      discount: descuento.value,
      payment: { method: metodoElegido.value, amountReceived: efectivo ? redondearDinero(Number(entregado.value)) : null },
    })
    ultimaVenta.value = respuesta
    carrito.value = []
    cobrando.value = false
    avisos.exito(`Venta registrada. Ticket #${respuesta.ticket}` + (respuesta.change > 0 ? `\nVuelto: ${dinero(respuesta.change)}` : ''))
    cargarProductos()
    sync.refrescar()
    enfocarCodigo()
  } catch (e) {
    errorCobro.value = e instanceof Error ? e.message : String(e)
  } finally {
    enviando.value = false
  }
}

// ------------------------------------------------------------------ teclado

function teclaGlobal(e: KeyboardEvent) {
  if (e.key === 'F4') {
    e.preventDefault()
    if (!cobrando.value) abrirCobro()
    return
  }
  const activo = document.activeElement?.tagName
  const hayModal = cobrando.value || rubro.value !== null || verVentaId.value !== null
  if (!hayModal && e.key.length === 1 && activo !== 'INPUT' && activo !== 'SELECT' && activo !== 'TEXTAREA') {
    inputCodigo.value?.focus()
  }
}
</script>

<template>
  <div class="p-4 lg:p-6 max-w-7xl mx-auto">
    <div v-if="caja.cargada && !caja.estado.open" class="mb-4">
      <AperturaCaja />
    </div>

    <div class="grid lg:grid-cols-3 gap-4">
      <!-- Ingreso y carrito -->
      <section class="lg:col-span-2 space-y-4">
        <div class="tarjeta p-4">
          <form class="flex flex-wrap gap-3 items-end" @submit.prevent="agregarPorCodigo">
            <div class="flex-1 min-w-48">
              <label class="etiqueta" for="codigo">Codigo de producto</label>
              <div class="relative">
                <i class="fa-solid fa-barcode absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"></i>
                <input id="codigo" ref="inputCodigo" v-model="codigo" class="campo !pl-9" placeholder="Escaneá o escribí y Enter" autocomplete="off" :disabled="!caja.estado.open" />
              </div>
            </div>
            <div class="w-24">
              <label class="etiqueta" for="cantidad">Cantidad</label>
              <input id="cantidad" v-model.number="cantidadIngreso" type="number" min="0.001" step="any" class="campo" :disabled="!caja.estado.open" />
            </div>
            <button class="btn-primario" :disabled="!caja.estado.open"><i class="fa-solid fa-plus"></i> Agregar</button>
          </form>

          <div class="mt-3 flex flex-wrap gap-2 items-start">
            <div class="relative flex-1 min-w-56">
              <label class="sr-only" for="buscar">Buscar por nombre</label>
              <input id="buscar" v-model="busqueda" class="campo" placeholder="Buscar por nombre..." autocomplete="off" :disabled="!caja.estado.open" />
              <ul v-if="resultados.length" class="absolute z-30 mt-1 w-full tarjeta max-h-72 overflow-y-auto shadow-lg">
                <li v-for="p in resultados" :key="p.id">
                  <button type="button" class="w-full text-left px-3 py-2 hover:bg-emerald-50 flex justify-between gap-3" @click="agregarDesdeBusqueda(p)">
                    <span>
                      <span class="font-medium">{{ p.name }}</span>
                      <span class="block text-xs text-slate-500">{{ p.barcode }} · {{ p.categoryName ?? 'Sin categoria' }}</span>
                    </span>
                    <span class="font-semibold text-emerald-700">{{ dinero(p.price) }}</span>
                  </button>
                </li>
              </ul>
            </div>
            <button
              v-for="(r, clave) in rubros"
              :key="clave"
              type="button"
              class="btn-secundario"
              :disabled="!caja.estado.open"
              :title="`${r.titulo}: venta por monto`"
              @click="abrirRubro(clave)"
            >
              <i :class="['fa-solid', r.icono, r.color]"></i> {{ r.titulo }}
            </button>
          </div>
        </div>

        <div class="tarjeta overflow-hidden">
          <table class="tabla w-full">
            <thead>
              <tr><th>Producto</th><th class="!text-center">Cantidad</th><th class="!text-right">Precio</th><th class="!text-right">Subtotal</th><th></th></tr>
            </thead>
            <tbody>
              <tr v-if="!carrito.length">
                <td colspan="5" class="text-center !py-10 text-slate-400"><i class="fa-solid fa-basket-shopping text-3xl mb-2 block"></i>Escaneá un producto para empezar</td>
              </tr>
              <tr v-for="l in carrito" :key="l.clave">
                <td>
                  <div class="font-medium">{{ l.nombre }}</div>
                  <div class="text-xs text-slate-400">{{ l.codigo }}</div>
                </td>
                <td class="text-center">
                  <span v-if="l.qtyVenta !== null" class="text-sm text-slate-600">{{ cantidad(l.qtyVenta) }} (por monto)</span>
                  <div v-else class="inline-flex items-center gap-1">
                    <button class="w-7 h-7 rounded border border-slate-300 hover:bg-slate-100" aria-label="Restar uno" @click="cambiarCantidad(l, -1)">-</button>
                    <input v-model.number="l.qty" type="number" min="0.001" step="any" class="w-20 text-center campo !py-1" :aria-label="`Cantidad de ${l.nombre}`" />
                    <button class="w-7 h-7 rounded border border-slate-300 hover:bg-slate-100" aria-label="Sumar uno" @click="cambiarCantidad(l, 1)">+</button>
                  </div>
                </td>
                <td class="text-right">{{ dinero(l.precio) }}</td>
                <td class="text-right font-semibold">{{ dinero(subtotalLinea(l)) }}</td>
                <td class="text-right">
                  <button class="text-slate-400 hover:text-rose-500 p-1" :aria-label="`Quitar ${l.nombre}`" @click="quitar(l)"><i class="fa-solid fa-trash-can"></i></button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Totales -->
      <aside class="space-y-4">
        <div class="tarjeta p-5 bg-slate-900 text-white border-slate-900">
          <div class="text-sm text-slate-400">Total a pagar</div>
          <div class="text-4xl font-bold text-emerald-400 mt-1">{{ dinero(total) }}</div>
          <div class="mt-4 space-y-1 text-sm text-slate-300">
            <div class="flex justify-between"><span>Subtotal</span><span>{{ dinero(subtotal) }}</span></div>
            <div class="flex justify-between"><span>Descuento combos</span><span>-{{ dinero(descuento) }}</span></div>
            <div class="flex justify-between"><span>Articulos</span><span>{{ cantidad(unidades) }}</span></div>
          </div>
          <ul v-if="descuentoCombos.aplicados.length" class="mt-3 text-xs text-emerald-300 space-y-1">
            <li v-for="c in descuentoCombos.aplicados" :key="c.nombre">
              <i class="fa-solid fa-layer-group mr-1"></i>Combo {{ c.nombre }}<span v-if="c.veces > 1"> x{{ c.veces }}</span>: {{ dinero(c.precioCombo) }} (antes {{ dinero(c.valorProductos) }})
            </li>
          </ul>
          <button class="btn-primario w-full mt-5 py-3 text-base" :disabled="!carrito.length || !caja.estado.open" @click="abrirCobro">
            <i class="fa-solid fa-cash-register"></i> Cobrar (F4)
          </button>
          <button class="btn w-full mt-2 text-slate-300 hover:bg-slate-800" :disabled="!carrito.length" @click="cancelarVenta">Cancelar venta</button>
        </div>

        <div v-if="ultimaVenta" class="tarjeta p-4 text-sm">
          <div class="flex justify-between items-center">
            <span class="font-semibold">Ultima venta #{{ ultimaVenta.ticket }}</span>
            <span class="font-bold">{{ dinero(ultimaVenta.total) }}</span>
          </div>
          <div v-if="ultimaVenta.change > 0" class="text-slate-500">Vuelto: {{ dinero(ultimaVenta.change) }}</div>
          <button class="btn-secundario w-full mt-3" @click="verVentaId = ultimaVenta.saleId"><i class="fa-solid fa-receipt"></i> Ver / anular</button>
        </div>
      </aside>
    </div>

    <!-- Cobro -->
    <Modal v-if="cobrando" titulo="Cobrar" :cerrable="!enviando" @cerrar="cobrando = false">
      <form id="form-cobro" class="space-y-4" @submit.prevent="confirmarCobro">
        <div class="flex justify-between items-baseline">
          <span class="text-slate-600">Total</span>
          <span class="text-3xl font-bold">{{ dinero(total) }}</span>
        </div>
        <fieldset>
          <legend class="etiqueta">Metodo de pago</legend>
          <div class="grid grid-cols-2 gap-2">
            <label
              v-for="m in metodos"
              :key="m.id"
              :class="['flex items-center gap-2 p-3 rounded-lg border cursor-pointer', metodoElegido === m.code ? 'border-emerald-500 bg-emerald-50' : 'border-slate-300']"
            >
              <input v-model="metodoElegido" type="radio" name="metodo" :value="m.code" class="accent-emerald-600" />
              <span class="font-medium">{{ m.name }}</span>
            </label>
          </div>
        </fieldset>
        <div v-if="metodo?.affectsCash" class="space-y-2">
          <label class="etiqueta" for="entregado">Monto entregado</label>
          <input id="entregado" v-model.number="entregado" type="number" min="0" step="0.01" class="campo text-lg" autofocus />
          <div class="flex flex-wrap gap-2">
            <button v-for="b in billetesRapidos" :key="b" type="button" class="btn-secundario !py-1" @click="entregado = b">{{ dinero(b) }}</button>
          </div>
          <div class="flex justify-between text-lg"><span>Vuelto</span><b class="text-emerald-700">{{ dinero(vuelto) }}</b></div>
        </div>
        <p v-if="errorCobro" class="text-sm text-rose-600">{{ errorCobro }}</p>
      </form>
      <template #pie>
        <button class="btn-secundario" :disabled="enviando" @click="cobrando = false">Cancelar</button>
        <button class="btn-primario" form="form-cobro" :disabled="enviando">
          <i class="fa-solid fa-check"></i> {{ enviando ? 'Registrando...' : 'Finalizar' }}
        </button>
      </template>
    </Modal>

    <!-- Venta por monto -->
    <Modal v-if="rubro" :titulo="rubros[rubro].titulo" @cerrar="rubro = null">
      <form id="form-rubro" class="space-y-4" @submit.prevent="agregarPorMonto">
        <div v-if="rubro !== 'verduleria'">
          <label class="etiqueta" for="rubro-producto">Producto</label>
          <div class="flex gap-2">
            <select id="rubro-producto" v-model="rubroProductoId" class="campo flex-1">
              <option v-for="p in productosDelRubro" :key="p.id" :value="p.id">{{ p.name }} · {{ dinero(p.price) }}/kg</option>
            </select>
            <button type="button" class="btn-secundario" title="Registrar producto" @click="rubroNuevo.visible = !rubroNuevo.visible"><i class="fa-solid fa-plus"></i></button>
          </div>
          <p v-if="!productosDelRubro.length" class="text-xs text-slate-500 mt-1">No hay productos en la categoria {{ rubros[rubro].categoria }}. Registrá uno con +.</p>
          <div v-if="rubroNuevo.visible" class="mt-3 p-3 rounded-lg bg-slate-50 border border-slate-200 grid grid-cols-2 gap-2">
            <input v-model="rubroNuevo.nombre" class="campo col-span-2" placeholder="Nombre (ej: Jamon cocido)" aria-label="Nombre del producto nuevo" />
            <input v-model.number="rubroNuevo.precio" type="number" min="0" step="0.01" class="campo" placeholder="Precio por kg" aria-label="Precio por kg" />
            <button type="button" class="btn-primario" @click="guardarNuevoDelRubro">Guardar</button>
          </div>
        </div>
        <div>
          <label class="etiqueta" for="rubro-monto">Monto a cobrar</label>
          <input id="rubro-monto" v-model.number="rubroMonto" type="number" min="0" step="0.01" class="campo text-lg" autofocus />
        </div>
      </form>
      <template #pie>
        <button class="btn-secundario" @click="rubro = null">Cancelar</button>
        <button class="btn-primario" form="form-rubro"><i class="fa-solid fa-plus"></i> Agregar</button>
      </template>
    </Modal>

    <DetalleVenta v-if="verVentaId" :venta-id="verVentaId" @cerrar="verVentaId = null" @anulada="ultimaVenta = null; cargarProductos()" />
  </div>
</template>
