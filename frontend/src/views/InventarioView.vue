<script setup lang="ts">
// Inventario (local) y Catalogo (web del duenio). En la web no se toca el stock.
import { computed, onMounted, ref } from 'vue'
import Modal from '@/components/Modal.vue'
import FormularioProducto from '@/components/FormularioProducto.vue'
import FormularioCombo from '@/components/FormularioCombo.vue'
import Categorias from '@/components/Categorias.vue'
import StockProducto from '@/components/StockProducto.vue'
import type { Categoria, Producto, Proveedor } from '@/api/tipos'
import { catalogo } from '@/datos/catalogo'
import { useAvisos } from '@/stores/avisos'
import { cantidad, dinero, normalizar } from '@/utils/formato'
import { esNube } from '@/modo'

const avisos = useAvisos()
const productos = ref<Producto[]>([])
const categorias = ref<Categoria[]>([])
const proveedores = ref<Proveedor[]>([])
const cargando = ref(true)

const filtro = ref({ texto: '', categoria: 'todas', estado: 'activos', tipo: 'todos' })

const editandoProducto = ref<Producto | 'nuevo' | null>(null)
const editandoCombo = ref<Producto | 'nuevo' | null>(null)
const viendoCategorias = ref(false)
const stockDe = ref<Producto | null>(null)

async function cargar() {
  cargando.value = true
  try {
    const [p, c, pr] = await Promise.all([catalogo.productos(), catalogo.categorias(), catalogo.proveedores()])
    productos.value = p
    categorias.value = c
    proveedores.value = pr
  } catch (e) {
    avisos.error(e)
  } finally {
    cargando.value = false
  }
}
onMounted(cargar)

const visibles = computed(() => {
  const texto = normalizar(filtro.value.texto)
  return productos.value.filter((p) => {
    if (texto && !normalizar(`${p.name} ${p.barcode} ${p.categoryName ?? ''}`).includes(texto)) return false
    if (filtro.value.categoria !== 'todas' && p.categoryId !== filtro.value.categoria) return false
    if (filtro.value.estado === 'activos' && !p.active) return false
    if (filtro.value.estado === 'inactivos' && p.active) return false
    if (filtro.value.tipo === 'productos' && p.combo) return false
    if (filtro.value.tipo === 'combos' && !p.combo) return false
    if (filtro.value.tipo === 'bajo' && (p.combo || p.stock > p.minStock)) return false
    return true
  })
})

const bajoStock = computed(() => productos.value.filter((p) => !p.combo && p.active && p.stock <= p.minStock).length)

function editar(p: Producto) {
  if (p.combo) editandoCombo.value = p
  else editandoProducto.value = p
}

async function guardado(mensaje: string) {
  editandoProducto.value = null
  editandoCombo.value = null
  avisos.exito(mensaje)
  await cargar()
}
</script>

<template>
  <div class="p-4 lg:p-6 max-w-7xl mx-auto space-y-4">
    <div class="flex flex-wrap items-center gap-2">
      <h1 class="text-2xl font-bold mr-auto">{{ esNube ? 'Catalogo' : 'Inventario' }}</h1>
      <button class="btn-secundario" @click="viendoCategorias = true"><i class="fa-solid fa-tags"></i> Categorias</button>
      <button class="btn-secundario" @click="editandoCombo = 'nuevo'"><i class="fa-solid fa-layer-group"></i> Nuevo combo</button>
      <button class="btn-primario" @click="editandoProducto = 'nuevo'"><i class="fa-solid fa-plus"></i> Nuevo producto</button>
    </div>

    <p v-if="esNube" class="text-sm text-slate-600 bg-sky-50 border border-sky-200 rounded-lg p-3">
      <i class="fa-solid fa-circle-info text-sky-600 mr-1"></i>
      Los cambios de precios y productos llegan a la PC del local en la próxima sincronización (cada 5 minutos si tiene internet).
      El stock se maneja en el local.
    </p>

    <div class="tarjeta p-4 grid sm:grid-cols-2 lg:grid-cols-4 gap-3">
      <div>
        <label class="etiqueta" for="f-texto">Buscar</label>
        <input id="f-texto" v-model="filtro.texto" class="campo" placeholder="Nombre o codigo..." />
      </div>
      <div>
        <label class="etiqueta" for="f-cat">Categoria</label>
        <select id="f-cat" v-model="filtro.categoria" class="campo">
          <option value="todas">Todas</option>
          <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}{{ c.activa ? '' : ' (baja)' }}</option>
        </select>
      </div>
      <div>
        <label class="etiqueta" for="f-estado">Estado</label>
        <select id="f-estado" v-model="filtro.estado" class="campo">
          <option value="activos">Activos</option>
          <option value="inactivos">Dados de baja</option>
          <option value="todos">Todos</option>
        </select>
      </div>
      <div>
        <label class="etiqueta" for="f-tipo">Mostrar</label>
        <select id="f-tipo" v-model="filtro.tipo" class="campo">
          <option value="todos">Productos y combos</option>
          <option value="productos">Solo productos</option>
          <option value="combos">Solo combos</option>
          <option value="bajo">Stock bajo ({{ bajoStock }})</option>
        </select>
      </div>
    </div>

    <div class="tarjeta overflow-x-auto">
      <table class="tabla w-full">
        <thead>
          <tr>
            <th>Codigo</th><th>Nombre</th><th>Categoria</th><th class="!text-right">Costo</th><th class="!text-right">Precio</th>
            <th class="!text-right">Stock</th><th class="!text-center">Estado</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="cargando"><td colspan="8" class="text-center !py-8 text-slate-400"><i class="fa-solid fa-spinner animate-spin"></i> Cargando...</td></tr>
          <tr v-else-if="!visibles.length"><td colspan="8" class="text-center !py-8 text-slate-400">No hay productos con esos filtros</td></tr>
          <tr v-for="p in visibles" :key="p.id" class="hover:bg-slate-50">
            <td class="font-mono text-xs">{{ p.barcode }}</td>
            <td>
              <span class="font-medium">{{ p.name }}</span>
              <span v-if="p.combo" class="insignia bg-violet-100 text-violet-700 ml-2">Combo</span>
            </td>
            <td class="text-slate-500">{{ p.categoryName ?? '-' }}</td>
            <td class="text-right text-slate-500">{{ dinero(p.cost) }}</td>
            <td class="text-right font-semibold text-emerald-700">{{ dinero(p.price) }}</td>
            <td class="text-right" :class="!p.combo && p.active && p.stock <= p.minStock ? 'text-rose-600 font-bold' : ''">
              <span v-if="p.combo && esNube" class="text-slate-400">-</span>
              <span v-else>{{ cantidad(p.stock) }}</span>
            </td>
            <td class="text-center">
              <span :class="['insignia', p.active ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-600']">{{ p.active ? 'Activo' : 'Baja' }}</span>
            </td>
            <td class="text-right whitespace-nowrap">
              <button v-if="!esNube && !p.combo" class="p-1.5 text-slate-400 hover:text-sky-600" title="Stock y movimientos" @click="stockDe = p">
                <i class="fa-solid fa-boxes-stacked"></i>
              </button>
              <button class="p-1.5 text-slate-400 hover:text-emerald-600" title="Editar" @click="editar(p)"><i class="fa-solid fa-pen"></i></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <FormularioProducto
      v-if="editandoProducto"
      :producto="editandoProducto === 'nuevo' ? null : editandoProducto"
      :categorias="categorias"
      :proveedores="proveedores"
      :productos="productos"
      @cerrar="editandoProducto = null"
      @guardado="guardado"
    />
    <FormularioCombo
      v-if="editandoCombo"
      :combo="editandoCombo === 'nuevo' ? null : editandoCombo"
      :categorias="categorias"
      :productos="productos"
      @cerrar="editandoCombo = null"
      @guardado="guardado"
    />
    <Modal v-if="viendoCategorias" titulo="Categorias" @cerrar="viendoCategorias = false; cargar()">
      <Categorias :categorias="categorias" :productos="productos" @cambio="cargar" />
    </Modal>
    <StockProducto v-if="stockDe" :producto="stockDe" @cerrar="stockDe = null" @ajustado="stockDe = null; cargar()" />
  </div>
</template>
