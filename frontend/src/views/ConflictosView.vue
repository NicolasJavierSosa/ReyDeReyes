<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { syncApi } from '@/api/local'
import type { Conflicto } from '@/api/tipos'
import { useAvisos } from '@/stores/avisos'
import { useSync } from '@/stores/sync'
import { fechaHora } from '@/utils/formato'

const avisos = useAvisos()
const sync = useSync()
const conflictos = ref<Conflicto[]>([])
const verRevisados = ref(false)

async function cargar() {
  try {
    conflictos.value = await syncApi.conflictos()
  } catch (e) {
    avisos.error(e)
  }
}
onMounted(cargar)

const visibles = computed(() => conflictos.value.filter((c) => verRevisados.value || !c.reviewed))

const nombresTabla: Record<string, string> = { productos: 'Producto', categorias: 'Categoria', tipos: 'Tipo', unidades_medida: 'Unidad' }
const ganador: Record<Conflicto['winner'], string> = {
  LOCAL: 'Quedó el cambio del local',
  NUBE: 'Quedó el cambio de la web',
  FUSION: 'Se unificaron (mismo codigo o nombre)',
}

/** Campos que cambiaron entre las dos versiones, en lenguaje simple. */
function diferencias(c: Conflicto) {
  try {
    const a = JSON.parse(c.winnerData ?? '{}')
    const b = JSON.parse(c.loserData ?? '{}')
    const ignorar = new Set(['version', 'actualizado_en', 'origen', 'id'])
    return Object.keys({ ...a, ...b })
      .filter((k) => !ignorar.has(k) && JSON.stringify(a[k]) !== JSON.stringify(b[k]))
      .map((k) => ({ campo: k.replace(/_/g, ' '), queda: a[k], descartado: b[k] }))
  } catch {
    return []
  }
}

function titulo(c: Conflicto) {
  try {
    const datos = JSON.parse(c.winnerData ?? c.loserData ?? '{}')
    return datos.nombre ?? datos.codigo_barra ?? c.entityId
  } catch {
    return c.entityId
  }
}

async function revisado(c: Conflicto) {
  try {
    await syncApi.marcarRevisado(c.id)
    await cargar()
    sync.refrescar()
  } catch (e) {
    avisos.error(e)
  }
}
</script>

<template>
  <div class="p-4 lg:p-6 max-w-5xl mx-auto space-y-4">
    <div class="flex flex-wrap items-center gap-2">
      <h1 class="text-2xl font-bold mr-auto">Conflictos de sincronizacion</h1>
      <label class="text-sm text-slate-600 flex items-center gap-1"><input v-model="verRevisados" type="checkbox" class="accent-emerald-600" /> Ver revisados</label>
    </div>
    <p class="text-sm text-slate-600">
      Pasa cuando el mismo producto se editó en el local y en la web antes de sincronizar. Gana el último cambio;
      acá podés ver qué valor se descartó y corregirlo a mano si hace falta.
    </p>
    <div v-if="!visibles.length" class="tarjeta p-8 text-center text-slate-400"><i class="fa-solid fa-circle-check text-3xl text-emerald-400 mb-2 block"></i>No hay conflictos para revisar</div>
    <div v-for="c in visibles" :key="c.id" class="tarjeta p-4 space-y-2">
      <div class="flex flex-wrap items-center gap-2">
        <span class="insignia bg-slate-200 text-slate-700">{{ nombresTabla[c.table] ?? c.table }}</span>
        <span class="font-semibold">{{ titulo(c) }}</span>
        <span class="text-xs text-slate-500 ml-auto">{{ fechaHora(c.detectedAt) }}</span>
      </div>
      <p class="text-sm text-slate-700">{{ ganador[c.winner] }}</p>
      <table v-if="diferencias(c).length" class="tabla w-full">
        <thead><tr><th>Campo</th><th>Valor que quedó</th><th>Valor descartado</th></tr></thead>
        <tbody>
          <tr v-for="d in diferencias(c)" :key="d.campo">
            <td class="capitalize">{{ d.campo }}</td>
            <td class="text-emerald-700">{{ d.queda ?? '-' }}</td>
            <td class="text-rose-600 line-through">{{ d.descartado ?? '-' }}</td>
          </tr>
        </tbody>
      </table>
      <div class="text-right"><button v-if="!c.reviewed" class="btn-secundario !py-1" @click="revisado(c)"><i class="fa-solid fa-check"></i> Marcar como revisado</button></div>
    </div>
  </div>
</template>
