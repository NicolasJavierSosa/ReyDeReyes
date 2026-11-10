<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import DetalleVenta from '@/components/DetalleVenta.vue'
import type { Uuid, VentaResumen } from '@/api/tipos'
import { consultas } from '@/datos/consultas'
import { useAvisos } from '@/stores/avisos'
import { dinero, fechaHora, hoyISO } from '@/utils/formato'

const avisos = useAvisos()
const desde = ref(hoyISO())
const hasta = ref(hoyISO())
const ventas = ref<VentaResumen[]>([])
const cargando = ref(false)
const verAnuladas = ref(false)
const ventaId = ref<Uuid | null>(null)

async function cargar() {
  cargando.value = true
  try {
    ventas.value = await consultas.ventas(desde.value, hasta.value)
  } catch (e) {
    avisos.error(e)
  } finally {
    cargando.value = false
  }
}
onMounted(cargar)

const visibles = computed(() => ventas.value.filter((v) => verAnuladas.value || v.status !== 'ANULADA'))
const completadas = computed(() => ventas.value.filter((v) => v.status === 'COMPLETADA'))
const total = computed(() => completadas.value.reduce((acc, v) => acc + Number(v.total), 0))
const porMedio = computed(() => {
  const mapa = new Map<string, number>()
  completadas.value.forEach((v) => mapa.set(v.paymentMethod ?? '-', (mapa.get(v.paymentMethod ?? '-') ?? 0) + Number(v.total)))
  return [...mapa.entries()].sort((a, b) => b[1] - a[1])
})
</script>

<template>
  <div class="p-4 lg:p-6 max-w-7xl mx-auto space-y-4">
    <h1 class="text-2xl font-bold">Ventas</h1>
    <form class="tarjeta p-3 flex flex-wrap gap-3 items-end" @submit.prevent="cargar">
      <div><label class="etiqueta" for="v-desde">Desde</label><input id="v-desde" v-model="desde" type="date" class="campo" required /></div>
      <div><label class="etiqueta" for="v-hasta">Hasta</label><input id="v-hasta" v-model="hasta" type="date" class="campo" required /></div>
      <button class="btn-secundario"><i class="fa-solid fa-filter"></i> Buscar</button>
      <label class="text-sm text-slate-600 flex items-center gap-1 ml-auto"><input v-model="verAnuladas" type="checkbox" class="accent-emerald-600" /> Mostrar anuladas</label>
    </form>

    <div class="grid sm:grid-cols-3 gap-3">
      <div class="tarjeta p-4"><div class="text-xs text-slate-500 uppercase">Total vendido</div><div class="text-2xl font-bold">{{ dinero(total) }}</div></div>
      <div class="tarjeta p-4"><div class="text-xs text-slate-500 uppercase">Ventas</div><div class="text-2xl font-bold">{{ completadas.length }}</div></div>
      <div class="tarjeta p-4 text-sm">
        <div class="text-xs text-slate-500 uppercase mb-1">Por medio de pago</div>
        <div v-for="[medio, monto] in porMedio" :key="medio" class="flex justify-between"><span>{{ medio }}</span><b>{{ dinero(monto) }}</b></div>
        <div v-if="!porMedio.length" class="text-slate-400">-</div>
      </div>
    </div>

    <div class="tarjeta overflow-x-auto">
      <table class="tabla w-full">
        <thead><tr><th>Ticket</th><th>Fecha</th><th class="!text-right">Items</th><th>Medio</th><th class="!text-right">Total</th><th>Estado</th></tr></thead>
        <tbody>
          <tr v-if="cargando"><td colspan="6" class="text-center !py-6 text-slate-400"><i class="fa-solid fa-spinner animate-spin"></i></td></tr>
          <tr v-else-if="!visibles.length"><td colspan="6" class="text-center !py-6 text-slate-400">No hay ventas en ese periodo</td></tr>
          <tr v-for="v in visibles" :key="v.id" class="hover:bg-slate-50 cursor-pointer" @click="ventaId = v.id">
            <td class="font-mono">#{{ v.ticket }}</td>
            <td class="whitespace-nowrap">{{ fechaHora(v.dateTime) }}</td>
            <td class="text-right">{{ v.items }}</td>
            <td>{{ v.paymentMethod ?? '-' }}</td>
            <td class="text-right font-semibold">{{ dinero(v.total) }}</td>
            <td><span :class="['insignia', v.status === 'ANULADA' ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-700']">{{ v.status === 'ANULADA' ? 'Anulada' : 'Completada' }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>
    <DetalleVenta v-if="ventaId" :venta-id="ventaId" @cerrar="ventaId = null" @anulada="cargar" />
  </div>
</template>
