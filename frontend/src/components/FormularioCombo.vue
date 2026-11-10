<script setup lang="ts">
import { computed, ref } from 'vue'
import Modal from './Modal.vue'
import type { Categoria, ComboRequest, Producto } from '@/api/tipos'
import { catalogo } from '@/datos/catalogo'
import { useAvisos } from '@/stores/avisos'
import { dinero, normalizar } from '@/utils/formato'

const props = defineProps<{ combo: Producto | null; categorias: Categoria[]; productos: Producto[] }>()
const emit = defineEmits<{ cerrar: []; guardado: [string] }>()
const avisos = useAvisos()

const c = props.combo
const modo = ref<'productos' | 'grupo'>(c?.comboGroupCategoryId ? 'grupo' : 'productos')
const form = ref({
  barcode: c && !c.barcode.startsWith('COMBO-') ? c.barcode : '',
  name: c?.name ?? '',
  description: c?.description ?? '',
  categoryId: c?.categoryId ?? null as string | null,
  price: c?.price ?? 0,
  active: c?.active ?? true,
  groupCategoryId: c?.comboGroupCategoryId ?? null as string | null,
  groupQuantity: c?.comboGroupQuantity ?? 2,
})
const elegidos = ref<string[]>(c?.comboItems ? [...c.comboItems] : [])
const busqueda = ref('')
const guardando = ref(false)

const candidatos = computed(() => {
  const texto = normalizar(busqueda.value)
  return props.productos
    .filter((p) => !p.combo && p.active && !elegidos.value.includes(p.id))
    .filter((p) => !texto || normalizar(`${p.name} ${p.barcode}`).includes(texto))
    .slice(0, 8)
})
const productosElegidos = computed(() => elegidos.value.map((id) => props.productos.find((p) => p.id === id)).filter(Boolean) as Producto[])
const sumaProductos = computed(() => productosElegidos.value.reduce((acc, p) => acc + Number(p.price), 0))

async function guardar() {
  if (modo.value === 'productos' && !elegidos.value.length) return avisos.mostrar('Agregá al menos un producto', 'error')
  if (modo.value === 'grupo' && (!form.value.groupCategoryId || !(form.value.groupQuantity > 0))) {
    return avisos.mostrar('Elegí la categoria y la cantidad del grupo', 'error')
  }
  const datos: ComboRequest = {
    barcode: form.value.barcode,
    name: form.value.name,
    description: form.value.description,
    categoryId: form.value.categoryId,
    unit: 'UN',
    type: 'Combo',
    cost: 0,
    price: Number(form.value.price) || 0,
    active: form.value.active,
    items: modo.value === 'productos' ? elegidos.value : null,
    groupCategoryId: modo.value === 'grupo' ? form.value.groupCategoryId : null,
    groupQuantity: modo.value === 'grupo' ? Number(form.value.groupQuantity) : null,
  }
  guardando.value = true
  try {
    if (c) await catalogo.actualizarCombo(c.id, datos)
    else await catalogo.crearCombo(datos)
    emit('guardado', c ? 'Combo actualizado' : 'Combo creado')
  } catch (e) {
    avisos.error(e)
  } finally {
    guardando.value = false
  }
}

async function cambiarEstado() {
  if (!c) return
  try {
    await catalogo.estadoProducto(c.id, !c.active)
    emit('guardado', c.active ? 'Combo dado de baja' : 'Combo reactivado')
  } catch (e) {
    avisos.error(e)
  }
}
</script>

<template>
  <Modal :titulo="c ? 'Modificar combo' : 'Nuevo combo'" ancho="max-w-2xl" @cerrar="emit('cerrar')">
    <form id="form-combo" class="space-y-4" @submit.prevent="guardar">
      <p class="text-sm text-slate-600 bg-violet-50 border border-violet-200 rounded-lg p-3">
        En la venta, si el cliente lleva los productos del combo se cobra el precio del combo automáticamente.
      </p>
      <div class="grid sm:grid-cols-2 gap-4">
        <div class="sm:col-span-2">
          <label class="etiqueta" for="c-nombre">Nombre del combo *</label>
          <input id="c-nombre" v-model="form.name" class="campo" required placeholder="Ej: Gaseosa + snack" />
        </div>
        <div>
          <label class="etiqueta" for="c-cat">Categoria</label>
          <select id="c-cat" v-model="form.categoryId" class="campo">
            <option :value="null">Sin categoria</option>
            <option v-for="cat in categorias.filter((x) => x.activa)" :key="cat.id" :value="cat.id">{{ cat.nombre }}</option>
          </select>
        </div>
        <div>
          <label class="etiqueta" for="c-codigo">Codigo (opcional)</label>
          <input id="c-codigo" v-model="form.barcode" class="campo" placeholder="Se genera solo" />
        </div>
      </div>

      <div class="flex gap-2">
        <button type="button" :class="modo === 'productos' ? 'btn-primario' : 'btn-secundario'" @click="modo = 'productos'">Productos fijos</button>
        <button type="button" :class="modo === 'grupo' ? 'btn-primario' : 'btn-secundario'" @click="modo = 'grupo'">N de una categoria</button>
      </div>

      <div v-if="modo === 'productos'" class="space-y-2">
        <input v-model="busqueda" class="campo" placeholder="Buscar producto para agregar..." aria-label="Buscar producto para el combo" />
        <ul v-if="busqueda && candidatos.length" class="tarjeta divide-y max-h-48 overflow-y-auto">
          <li v-for="p in candidatos" :key="p.id">
            <button type="button" class="w-full text-left px-3 py-2 hover:bg-emerald-50 flex justify-between" @click="elegidos.push(p.id); busqueda = ''">
              <span>{{ p.name }} <span class="text-xs text-slate-400">{{ p.barcode }}</span></span><span>{{ dinero(p.price) }}</span>
            </button>
          </li>
        </ul>
        <ul class="tarjeta divide-y">
          <li v-if="!productosElegidos.length" class="px-3 py-3 text-sm text-slate-400">Todavia no hay productos en el combo</li>
          <li v-for="p in productosElegidos" :key="p.id" class="px-3 py-2 flex justify-between items-center text-sm">
            <span>{{ p.name }}</span>
            <span class="flex items-center gap-3">{{ dinero(p.price) }}
              <button type="button" class="text-slate-400 hover:text-rose-500" :aria-label="`Quitar ${p.name}`" @click="elegidos = elegidos.filter((id) => id !== p.id)"><i class="fa-solid fa-xmark"></i></button>
            </span>
          </li>
        </ul>
      </div>
      <div v-else class="grid grid-cols-2 gap-4">
        <div>
          <label class="etiqueta" for="c-gcat">Categoria del grupo *</label>
          <select id="c-gcat" v-model="form.groupCategoryId" class="campo">
            <option :value="null" disabled>Elegí una categoria</option>
            <option v-for="cat in categorias.filter((x) => x.activa)" :key="cat.id" :value="cat.id">{{ cat.nombre }}</option>
          </select>
        </div>
        <div>
          <label class="etiqueta" for="c-gcant">Cantidad de unidades *</label>
          <input id="c-gcant" v-model.number="form.groupQuantity" type="number" min="1" step="1" class="campo" />
        </div>
      </div>

      <div>
        <label class="etiqueta" for="c-precio">Precio del combo *</label>
        <div class="flex gap-2">
          <input id="c-precio" v-model.number="form.price" type="number" min="0" step="0.01" class="campo" required />
          <button v-if="modo === 'productos' && sumaProductos > 0" type="button" class="btn-secundario whitespace-nowrap" @click="form.price = sumaProductos">
            Suma: {{ dinero(sumaProductos) }}
          </button>
        </div>
      </div>
    </form>
    <template #pie>
      <button v-if="c" type="button" class="btn text-rose-600 hover:bg-rose-50 mr-auto" @click="cambiarEstado">
        {{ c.active ? 'Dar de baja' : 'Reactivar' }}
      </button>
      <button class="btn-secundario" @click="emit('cerrar')">Cancelar</button>
      <button class="btn-primario" form="form-combo" :disabled="guardando"><i class="fa-solid fa-floppy-disk"></i> Guardar</button>
    </template>
  </Modal>
</template>
