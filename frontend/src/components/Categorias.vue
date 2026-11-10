<script setup lang="ts">
import { ref } from 'vue'
import type { Categoria, Producto } from '@/api/tipos'
import { catalogo } from '@/datos/catalogo'
import { useAvisos } from '@/stores/avisos'

const props = defineProps<{ categorias: Categoria[]; productos: Producto[] }>()
const emit = defineEmits<{ cambio: [] }>()
const avisos = useAvisos()
const nueva = ref('')
const editando = ref<{ id: string; nombre: string } | null>(null)

async function ejecutar(accion: () => Promise<unknown>, mensaje: string) {
  try {
    await accion()
    avisos.exito(mensaje)
    emit('cambio')
  } catch (e) {
    avisos.error(e)
  }
}

function crear() {
  if (!nueva.value.trim()) return
  ejecutar(() => catalogo.crearCategoria(nueva.value), 'Categoria creada').then(() => (nueva.value = ''))
}

function renombrar() {
  if (!editando.value?.nombre.trim()) return
  const { id, nombre } = editando.value
  ejecutar(() => catalogo.renombrarCategoria(id, nombre), 'Categoria renombrada').then(() => (editando.value = null))
}

function cambiarEstado(c: Categoria) {
  const usados = props.productos.filter((p) => p.categoryId === c.id && p.active).length
  if (c.activa && usados && !confirm(`La categoria tiene ${usados} productos activos. ¿Darla de baja igual?`)) return
  ejecutar(() => catalogo.estadoCategoria(c.id, !c.activa), c.activa ? 'Categoria dada de baja' : 'Categoria reactivada')
}
</script>

<template>
  <div class="space-y-4">
    <form class="flex gap-2" @submit.prevent="crear">
      <input v-model="nueva" class="campo" placeholder="Nueva categoria (ej: Limpieza)" aria-label="Nombre de la nueva categoria" />
      <button class="btn-primario"><i class="fa-solid fa-plus"></i> Agregar</button>
    </form>
    <ul class="divide-y tarjeta">
      <li v-for="c in categorias" :key="c.id" class="px-3 py-2 flex items-center gap-2">
        <template v-if="editando?.id === c.id">
          <input v-model="editando.nombre" class="campo flex-1 !py-1" aria-label="Nuevo nombre" @keydown.enter.prevent="renombrar" />
          <button class="btn-primario !py-1" @click="renombrar">Guardar</button>
          <button class="btn-secundario !py-1" @click="editando = null">Cancelar</button>
        </template>
        <template v-else>
          <span :class="['flex-1', c.activa ? '' : 'text-slate-400 line-through']">{{ c.nombre }}</span>
          <span class="text-xs text-slate-400">{{ productos.filter((p) => p.categoryId === c.id).length }} prod.</span>
          <button class="p-1 text-slate-400 hover:text-emerald-600" title="Renombrar" @click="editando = { id: c.id, nombre: c.nombre }"><i class="fa-solid fa-pen"></i></button>
          <button class="p-1 text-slate-400 hover:text-rose-600" :title="c.activa ? 'Dar de baja' : 'Reactivar'" @click="cambiarEstado(c)">
            <i :class="['fa-solid', c.activa ? 'fa-ban' : 'fa-rotate-left']"></i>
          </button>
        </template>
      </li>
    </ul>
  </div>
</template>
