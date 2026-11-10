<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { auth, metodosPagoApi, syncApi } from '@/api/local'
import type { MetodoPago } from '@/api/tipos'
import { useAvisos } from '@/stores/avisos'
import { useSync } from '@/stores/sync'
import { fechaHora } from '@/utils/formato'

const avisos = useAvisos()
const sync = useSync()
const metodos = ref<MetodoPago[]>([])
const nuevoMetodo = ref({ code: '', name: '', affectsCash: false })
const clave = ref({ actual: '', nueva: '', repetir: '' })

async function cargar() {
  try {
    metodos.value = await metodosPagoApi.listar(false)
  } catch (e) {
    avisos.error(e)
  }
  sync.refrescar()
}
onMounted(cargar)

async function guardarMetodo(m: MetodoPago) {
  try {
    await metodosPagoApi.actualizar(m.id, { code: m.code, name: m.name, affectsCash: m.affectsCash, active: m.active, order: m.order })
    avisos.exito('Metodo de pago actualizado')
    await cargar()
  } catch (e) {
    avisos.error(e)
  }
}

async function crearMetodo() {
  const n = nuevoMetodo.value
  if (!n.name.trim()) return
  try {
    await metodosPagoApi.crear({ code: n.code || n.name, name: n.name, affectsCash: n.affectsCash, active: true, order: metodos.value.length + 1 })
    nuevoMetodo.value = { code: '', name: '', affectsCash: false }
    avisos.exito('Metodo de pago agregado')
    await cargar()
  } catch (e) {
    avisos.error(e)
  }
}

async function cambiarClave() {
  if (clave.value.nueva !== clave.value.repetir) return avisos.mostrar('Las claves nuevas no coinciden', 'error')
  try {
    await auth.cambiarClave(clave.value.actual, clave.value.nueva)
    clave.value = { actual: '', nueva: '', repetir: '' }
    avisos.exito('Clave cambiada')
  } catch (e) {
    avisos.error(e)
  }
}

async function sincronizar() {
  const r = await sync.ahora()
  if (r.ok) avisos.exito(`Sincronizado: ${r.pushed} subidos, ${r.pulled} bajados, ${r.conflicts} conflictos`)
  else avisos.mostrar(r.message, 'error')
}

async function restaurar() {
  if (!confirm('Esto baja TODOS los datos desde la nube. Solo funciona en una PC nueva con la base vacia. ¿Continuar?')) return
  try {
    const r = await syncApi.restaurar()
    avisos.exito(r.message)
    await cargar()
  } catch (e) {
    avisos.error(e)
  }
}
</script>

<template>
  <div class="p-4 lg:p-6 max-w-4xl mx-auto space-y-6">
    <h1 class="text-2xl font-bold">Configuracion</h1>

    <section class="tarjeta p-5 space-y-3">
      <h2 class="font-semibold text-lg"><i class="fa-solid fa-wallet mr-2 text-emerald-600"></i>Metodos de pago</h2>
      <p class="text-sm text-slate-600">Solo registran cómo se cobró. Marcá "Es efectivo" en los que suman al dinero físico de la caja.</p>
      <table class="tabla w-full">
        <thead><tr><th>Nombre</th><th>Codigo</th><th class="!text-center">Es efectivo</th><th class="!text-center">Activo</th><th class="!text-center">Orden</th><th></th></tr></thead>
        <tbody>
          <tr v-for="m in metodos" :key="m.id">
            <td><input v-model="m.name" class="campo !py-1" :aria-label="`Nombre de ${m.code}`" /></td>
            <td class="font-mono text-xs">{{ m.code }}</td>
            <td class="text-center"><input v-model="m.affectsCash" type="checkbox" class="accent-emerald-600 w-4 h-4" :aria-label="`${m.name} es efectivo`" /></td>
            <td class="text-center"><input v-model="m.active" type="checkbox" class="accent-emerald-600 w-4 h-4" :aria-label="`${m.name} activo`" /></td>
            <td class="text-center"><input v-model.number="m.order" type="number" class="campo !py-1 w-16 text-center" :aria-label="`Orden de ${m.name}`" /></td>
            <td class="text-right"><button class="btn-secundario !py-1" @click="guardarMetodo(m)">Guardar</button></td>
          </tr>
        </tbody>
      </table>
      <form class="flex flex-wrap gap-2 items-end" @submit.prevent="crearMetodo">
        <div class="flex-1 min-w-40"><label class="etiqueta" for="mp-nombre">Nuevo metodo</label><input id="mp-nombre" v-model="nuevoMetodo.name" class="campo" placeholder="Ej: Cuenta DNI" /></div>
        <label class="text-sm flex items-center gap-1 pb-2"><input v-model="nuevoMetodo.affectsCash" type="checkbox" class="accent-emerald-600" /> Es efectivo</label>
        <button class="btn-primario"><i class="fa-solid fa-plus"></i> Agregar</button>
      </form>
    </section>

    <section class="tarjeta p-5 space-y-3">
      <h2 class="font-semibold text-lg"><i class="fa-solid fa-cloud mr-2 text-sky-600"></i>Sincronizacion con la nube</h2>
      <div v-if="sync.estado" class="grid sm:grid-cols-2 gap-2 text-sm">
        <div>Estado: <b>{{ sync.estado.enabled ? sync.estado.level : 'No configurada' }}</b></div>
        <div>Cambios sin subir: <b>{{ sync.estado.pendingChanges }}</b></div>
        <div>Ultima subida: <b>{{ fechaHora(sync.estado.lastSuccess) }}</b></div>
        <div>Ultimo intento: <b>{{ fechaHora(sync.estado.lastAttempt) }}</b></div>
        <div v-if="sync.estado.lastError" class="sm:col-span-2 text-rose-600">Ultimo error: {{ sync.estado.lastError }}</div>
      </div>
      <div class="flex flex-wrap gap-2">
        <button class="btn-primario" :disabled="sync.sincronizando" @click="sincronizar"><i class="fa-solid fa-rotate" :class="{ 'animate-spin': sync.sincronizando }"></i> Sincronizar ahora</button>
        <button class="btn-secundario" @click="restaurar"><i class="fa-solid fa-cloud-arrow-down"></i> Restaurar PC nueva desde la nube</button>
      </div>
      <p class="text-xs text-slate-500">La conexion a Supabase se configura en config-local.bat (ver docs/PUESTA-EN-MARCHA.md).</p>
    </section>

    <section class="tarjeta p-5 space-y-3">
      <h2 class="font-semibold text-lg"><i class="fa-solid fa-key mr-2 text-amber-500"></i>Cambiar clave</h2>
      <form class="grid sm:grid-cols-3 gap-3 items-end" @submit.prevent="cambiarClave">
        <div><label class="etiqueta" for="cl-actual">Clave actual</label><input id="cl-actual" v-model="clave.actual" type="password" class="campo" required autocomplete="current-password" /></div>
        <div><label class="etiqueta" for="cl-nueva">Clave nueva</label><input id="cl-nueva" v-model="clave.nueva" type="password" class="campo" required minlength="6" autocomplete="new-password" /></div>
        <div><label class="etiqueta" for="cl-rep">Repetir</label><input id="cl-rep" v-model="clave.repetir" type="password" class="campo" required minlength="6" autocomplete="new-password" /></div>
        <button class="btn-primario sm:col-span-3 justify-self-start">Cambiar clave</button>
      </form>
    </section>
  </div>
</template>
