<script setup lang="ts">
import { computed, ref } from 'vue'
import Modal from './Modal.vue'
import { productosApi } from '@/api/local'
import type { Categoria, Producto, ProductoRequest, Proveedor } from '@/api/tipos'
import { catalogo } from '@/datos/catalogo'
import { useAvisos } from '@/stores/avisos'
import { esNube } from '@/modo'

const props = defineProps<{ producto: Producto | null; categorias: Categoria[]; proveedores: Proveedor[]; productos: Producto[] }>()
const emit = defineEmits<{ cerrar: []; guardado: [string] }>()
const avisos = useAvisos()

const p = props.producto
const form = ref<ProductoRequest>({
  barcode: p?.barcode ?? '',
  name: p?.name ?? '',
  description: p?.description ?? '',
  categoryId: p?.categoryId ?? null,
  supplierId: p?.supplierId ?? null,
  unit: p?.unit ?? 'UN',
  type: p?.type ?? 'Producto',
  cost: p?.cost ?? 0,
  price: p?.price ?? 0,
  stock: p ? p.stock : 0,
  stockOriginal: p ? p.stock : null,
  minStock: p?.minStock ?? 0,
  active: p?.active ?? true,
})
const guardando = ref(false)
const buscandoOff = ref(false)

const tipos = computed(() => [...new Set(props.productos.map((x) => x.type).filter(Boolean))] as string[])
const categoriasActivas = computed(() => props.categorias.filter((c) => c.activa || c.id === form.value.categoryId))
const margen = computed(() => {
  const costo = Number(form.value.cost) || 0
  const precio = Number(form.value.price) || 0
  return costo > 0 ? Math.round(((precio - costo) / costo) * 100) : null
})

async function buscarOpenFoodFacts() {
  if (!form.value.barcode.trim()) return
  buscandoOff.value = true
  try {
    const r = await productosApi.openFoodFacts(form.value.barcode.trim())
    if (r.found) {
      if (!form.value.name) form.value.name = [r.name, r.brand].filter(Boolean).join(' ')
      if (!form.value.description) form.value.description = [r.description, r.quantity].filter(Boolean).join(' - ')
      avisos.exito('Datos completados desde Open Food Facts')
    } else {
      avisos.mostrar(r.reason === 'invalid_barcode' ? 'Codigo no valido para buscar' : 'No se encontro el producto', 'info')
    }
  } catch {
    avisos.mostrar('No se pudo consultar (¿sin internet?)', 'info')
  } finally {
    buscandoOff.value = false
  }
}

async function guardar() {
  if (!form.value.categoryId) return avisos.mostrar('Elegí una categoria', 'error')
  guardando.value = true
  try {
    const datos = { ...form.value, supplierId: form.value.supplierId || null }
    if (p) await catalogo.actualizarProducto(p.id, datos)
    else await catalogo.crearProducto(datos)
    emit('guardado', p ? 'Producto actualizado' : 'Producto creado')
  } catch (e) {
    avisos.error(e)
  } finally {
    guardando.value = false
  }
}

async function cambiarEstado() {
  if (!p) return
  if (p.active && !confirm(`¿Dar de baja "${p.name}"? No aparecerá en ventas.`)) return
  try {
    await catalogo.estadoProducto(p.id, !p.active)
    emit('guardado', p.active ? 'Producto dado de baja' : 'Producto reactivado')
  } catch (e) {
    avisos.error(e)
  }
}
</script>

<template>
  <Modal :titulo="p ? 'Modificar producto' : 'Nuevo producto'" ancho="max-w-2xl" @cerrar="emit('cerrar')">
    <form id="form-producto" class="grid sm:grid-cols-2 gap-4" @submit.prevent="guardar">
      <div class="sm:col-span-2">
        <label class="etiqueta" for="p-codigo">Codigo de barras / interno *</label>
        <div class="flex gap-2">
          <input id="p-codigo" v-model="form.barcode" class="campo flex-1" required maxlength="64" autofocus />
          <button v-if="!esNube" type="button" class="btn-secundario" :disabled="buscandoOff" title="Buscar datos en Open Food Facts" @click="buscarOpenFoodFacts">
            <i class="fa-solid fa-magnifying-glass" :class="{ 'animate-spin': buscandoOff }"></i>
          </button>
        </div>
      </div>
      <div class="sm:col-span-2">
        <label class="etiqueta" for="p-nombre">Nombre *</label>
        <input id="p-nombre" v-model="form.name" class="campo" required maxlength="120" placeholder="Ej: Coca Cola 2.25L" />
      </div>
      <div class="sm:col-span-2">
        <label class="etiqueta" for="p-desc">Descripcion</label>
        <input id="p-desc" v-model="form.description" class="campo" maxlength="500" />
      </div>
      <div>
        <label class="etiqueta" for="p-cat">Categoria *</label>
        <select id="p-cat" v-model="form.categoryId" class="campo" required>
          <option :value="null" disabled>Elegí una categoria</option>
          <option v-for="c in categoriasActivas" :key="c.id" :value="c.id">{{ c.nombre }}</option>
        </select>
      </div>
      <div>
        <label class="etiqueta" for="p-prov">Proveedor</label>
        <select id="p-prov" v-model="form.supplierId" class="campo">
          <option :value="null">Sin proveedor</option>
          <option v-for="pr in proveedores.filter((x) => x.active || x.phone === form.supplierId)" :key="pr.phone" :value="pr.phone">{{ pr.name }}</option>
        </select>
      </div>
      <div>
        <label class="etiqueta" for="p-unidad">Unidad *</label>
        <select id="p-unidad" v-model="form.unit" class="campo">
          <option value="UN">Unidad</option><option value="KG">Kilogramo</option><option value="G">Gramo</option>
          <option value="L">Litro</option><option value="ML">Mililitro</option>
        </select>
      </div>
      <div>
        <label class="etiqueta" for="p-tipo">Tipo *</label>
        <input id="p-tipo" v-model="form.type" class="campo" list="lista-tipos" required />
        <datalist id="lista-tipos"><option v-for="t in tipos" :key="t" :value="t" /></datalist>
      </div>
      <div>
        <label class="etiqueta" for="p-costo">Precio de costo</label>
        <input id="p-costo" v-model.number="form.cost" type="number" min="0" step="0.01" class="campo" />
      </div>
      <div>
        <label class="etiqueta" for="p-precio">Precio de venta * <span v-if="margen !== null" class="normal-case text-emerald-600">(margen {{ margen }}%)</span></label>
        <input id="p-precio" v-model.number="form.price" type="number" min="0" step="0.01" class="campo" required />
      </div>
      <div v-if="!esNube">
        <label class="etiqueta" for="p-stock">{{ p ? 'Stock actual' : 'Stock inicial' }}</label>
        <input id="p-stock" v-model.number="form.stock" type="number" min="0" step="any" class="campo" />
        <p v-if="p" class="text-xs text-slate-500 mt-1">Si lo cambiás se registra un ajuste solo por la diferencia.</p>
      </div>
      <div>
        <label class="etiqueta" for="p-min">Stock minimo</label>
        <input id="p-min" v-model.number="form.minStock" type="number" min="0" step="any" class="campo" />
      </div>
    </form>
    <template #pie>
      <button v-if="p" type="button" :class="p.active ? 'btn text-rose-600 hover:bg-rose-50 mr-auto' : 'btn text-emerald-700 hover:bg-emerald-50 mr-auto'" @click="cambiarEstado">
        <i :class="['fa-solid', p.active ? 'fa-ban' : 'fa-circle-check']"></i> {{ p.active ? 'Dar de baja' : 'Reactivar' }}
      </button>
      <button class="btn-secundario" @click="emit('cerrar')">Cancelar</button>
      <button class="btn-primario" form="form-producto" :disabled="guardando"><i class="fa-solid fa-floppy-disk"></i> Guardar</button>
    </template>
  </Modal>
</template>
