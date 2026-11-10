<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useSync } from '@/stores/sync'
import { useAvisos } from '@/stores/avisos'
import { fechaHora } from '@/utils/formato'

const sync = useSync()
const avisos = useAvisos()
onMounted(() => sync.iniciar())

const color = computed(() => ({
  VERDE: 'bg-emerald-400',
  AMARILLO: 'bg-amber-400',
  ROJO: 'bg-rose-500 animate-pulse',
  DESACTIVADA: 'bg-slate-500',
}[sync.estado?.level ?? 'DESACTIVADA']))

const texto = computed(() => {
  const e = sync.estado
  if (!e) return 'Nube'
  if (e.level === 'DESACTIVADA') return 'Nube no configurada'
  if (e.pendingChanges === 0) return 'Al dia'
  return `${e.pendingChanges} sin subir`
})

const detalle = computed(() => {
  const e = sync.estado
  if (!e) return ''
  const partes = [`Ultima subida: ${fechaHora(e.lastSuccess)}`]
  if (e.lastError) partes.push(`Ultimo error: ${e.lastError}`)
  if (e.unreviewedConflicts) partes.push(`${e.unreviewedConflicts} conflictos para revisar`)
  return partes.join('\n')
})

async function sincronizar() {
  const r = await sync.ahora()
  if (r.ok) avisos.exito(`Sincronizado: ${r.pushed} subidos, ${r.pulled} bajados`)
  else avisos.mostrar(r.message, 'error')
}
</script>

<template>
  <button
    type="button"
    class="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-sm text-slate-200"
    :title="detalle"
    :disabled="sync.sincronizando"
    @click="sincronizar"
  >
    <span :class="['inline-block w-2.5 h-2.5 rounded-full', color]"></span>
    <span>{{ sync.sincronizando ? 'Sincronizando...' : texto }}</span>
    <i class="fa-solid fa-rotate text-xs" :class="{ 'animate-spin': sync.sincronizando }"></i>
  </button>
</template>
