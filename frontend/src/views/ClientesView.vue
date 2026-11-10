<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Modal from '@/components/Modal.vue'
import { clientesApi } from '@/api/local'
import type { Cliente } from '@/api/tipos'
import { useAvisos } from '@/stores/avisos'
import { normalizar } from '@/utils/formato'

const avisos = useAvisos()
const clientes = ref<Cliente[]>([])
const texto = ref('')
const estado = ref<'active' | 'inactive' | 'all'>('active')
const editando = ref<{ id: string | null; firstName: string; lastName: string; phone: string; email: string } | null>(null)

async function cargar() {
  try {
    clientes.value = await clientesApi.listar('all')
  } catch (e) {
    avisos.error(e)
  }
}
onMounted(cargar)

const visibles = computed(() => {
  const t = normalizar(texto.value)
  return clientes.value
    .filter((c) => estado.value === 'all' || (estado.value === 'active') === c.active)
    .filter((c) => !t || normalizar(`${c.firstName} ${c.lastName} ${c.phone} ${c.email}`).includes(t))
})

async function guardar() {
  const e = editando.value!
  const datos = { firstName: e.firstName, lastName: e.lastName, phone: e.phone, email: e.email }
  try {
    if (e.id) await clientesApi.actualizar(e.id, datos)
    else await clientesApi.crear(datos)
    avisos.exito(e.id ? 'Cliente actualizado' : 'Cliente registrado')
    editando.value = null
    await cargar()
  } catch (err) {
    avisos.error(err)
  }
}

async function cambiarEstado(c: Cliente) {
  try {
    await clientesApi.cambiarEstado(c.id, !c.active)
    await cargar()
  } catch (e) {
    avisos.error(e)
  }
}
</script>

<template>
  <div class="p-4 lg:p-6 max-w-6xl mx-auto space-y-4">
    <div class="flex items-center gap-2">
      <h1 class="text-2xl font-bold mr-auto">Clientes</h1>
      <button class="btn-primario" @click="editando = { id: null, firstName: '', lastName: '', phone: '', email: '' }"><i class="fa-solid fa-user-plus"></i> Nuevo cliente</button>
    </div>
    <div class="tarjeta p-3 flex flex-wrap gap-3">
      <input v-model="texto" class="campo max-w-sm" placeholder="Nombre, telefono o email..." aria-label="Buscar cliente" />
      <select v-model="estado" class="campo w-40" aria-label="Estado">
        <option value="active">Activos</option><option value="inactive">Inactivos</option><option value="all">Todos</option>
      </select>
    </div>
    <div class="tarjeta overflow-x-auto">
      <table class="tabla w-full">
        <thead><tr><th>Nombre</th><th>Apellido</th><th>Telefono</th><th>Email</th><th>Estado</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!visibles.length"><td colspan="6" class="text-center !py-6 text-slate-400">No hay clientes</td></tr>
          <tr v-for="c in visibles" :key="c.id">
            <td>{{ c.firstName }}</td><td class="font-semibold">{{ c.lastName }}</td><td>{{ c.phone }}</td><td>{{ c.email }}</td>
            <td><span :class="['insignia', c.active ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-600']">{{ c.active ? 'Activo' : 'Inactivo' }}</span></td>
            <td class="text-right whitespace-nowrap">
              <button class="p-1.5 text-slate-400 hover:text-emerald-600" title="Editar" @click="editando = { id: c.id, firstName: c.firstName, lastName: c.lastName, phone: c.phone, email: c.email }"><i class="fa-solid fa-pen"></i></button>
              <button class="p-1.5 text-slate-400 hover:text-rose-600" :title="c.active ? 'Desactivar' : 'Reactivar'" @click="cambiarEstado(c)"><i :class="['fa-solid', c.active ? 'fa-ban' : 'fa-rotate-left']"></i></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal v-if="editando" :titulo="editando.id ? 'Modificar cliente' : 'Nuevo cliente'" @cerrar="editando = null">
      <form id="form-cliente" class="grid grid-cols-2 gap-3" @submit.prevent="guardar">
        <div><label class="etiqueta" for="cl-nombre">Nombre *</label><input id="cl-nombre" v-model="editando.firstName" class="campo" required autofocus /></div>
        <div><label class="etiqueta" for="cl-apellido">Apellido *</label><input id="cl-apellido" v-model="editando.lastName" class="campo" required /></div>
        <div><label class="etiqueta" for="cl-tel">Telefono *</label><input id="cl-tel" v-model="editando.phone" class="campo" required /></div>
        <div><label class="etiqueta" for="cl-email">Email *</label><input id="cl-email" v-model="editando.email" type="email" class="campo" required /></div>
      </form>
      <template #pie>
        <button class="btn-secundario" @click="editando = null">Cancelar</button>
        <button class="btn-primario" form="form-cliente">Guardar</button>
      </template>
    </Modal>
  </div>
</template>
