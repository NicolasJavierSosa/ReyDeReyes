<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Bar, Doughnut, Line } from 'vue-chartjs'
import {
  ArcElement, BarElement, CategoryScale, Chart, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip,
} from 'chart.js'
import { estadisticasApi } from '@/api/local'
import type { Estadisticas } from '@/api/tipos'
import { useAvisos } from '@/stores/avisos'
import { cantidad, dinero, fecha } from '@/utils/formato'

Chart.register(ArcElement, BarElement, CategoryScale, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip)

const avisos = useAvisos()
const periodo = ref<'hoy' | 'semana' | 'mes'>('semana')
const datos = ref<Estadisticas | null>(null)
const cargando = ref(false)

async function cargar() {
  cargando.value = true
  try {
    datos.value = await estadisticasApi.obtener(periodo.value)
  } catch (e) {
    avisos.error(e)
  } finally {
    cargando.value = false
  }
}
onMounted(cargar)

function imprimir() {
  window.print()
}

const verde = '#10b981'
const opciones = { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } }
const ticket = computed(() => ({
  labels: datos.value?.ticketPromedio.labels ?? [],
  datasets: [{ data: datos.value?.ticketPromedio.data ?? [], borderColor: verde, backgroundColor: 'rgba(16,185,129,0.15)', fill: true, tension: 0.3 }],
}))
const top = computed(() => ({
  labels: datos.value?.topProducts.labels ?? [],
  datasets: [{ data: datos.value?.topProducts.data ?? [], backgroundColor: verde, borderRadius: 6 }],
}))
const rotacion = computed(() => ({
  labels: datos.value?.rotation.labels ?? [],
  datasets: [{ data: datos.value?.rotation.data ?? [], backgroundColor: '#0ea5e9', borderRadius: 6 }],
}))
const colores = ['#10b981', '#0ea5e9', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#64748b']
const categorias = computed(() => ({
  labels: datos.value?.categories.map((c) => c.name) ?? [],
  datasets: [{ data: datos.value?.categories.map((c) => c.revenue) ?? [], backgroundColor: colores }],
}))

const tarjetas = computed(() => {
  const k = datos.value?.kpis
  if (!k) return []
  return [
    { titulo: 'Ingresos', valor: dinero(k.revenue), tendencia: k.trends.rev },
    { titulo: 'Ganancia bruta', valor: dinero(k.profit), tendencia: k.trends.prof },
    { titulo: 'Unidades vendidas', valor: cantidad(k.items), tendencia: k.trends.items },
    { titulo: 'Ventas', valor: String(k.salesCount), tendencia: k.trends.salesCount },
  ]
})
</script>

<template>
  <div class="p-4 lg:p-6 max-w-7xl mx-auto space-y-4">
    <div class="flex flex-wrap items-center gap-2">
      <h1 class="text-2xl font-bold mr-auto">Estadisticas</h1>
      <select v-model="periodo" class="campo w-40 no-imprimir" aria-label="Periodo" @change="cargar">
        <option value="hoy">Hoy</option><option value="semana">Esta semana</option><option value="mes">Este mes</option>
      </select>
      <button class="btn-secundario no-imprimir" @click="imprimir"><i class="fa-solid fa-print"></i> Imprimir / PDF</button>
    </div>
    <p v-if="datos" class="text-sm text-slate-500">Del {{ fecha(datos.from) }} al {{ fecha(datos.to) }}, comparado con el periodo anterior.</p>

    <div class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <div v-for="t in tarjetas" :key="t.titulo" class="tarjeta p-4">
        <div class="text-xs text-slate-500 uppercase">{{ t.titulo }}</div>
        <div class="text-2xl font-bold">{{ t.valor }}</div>
        <div :class="['text-xs font-semibold', t.tendencia.startsWith('-') ? 'text-rose-600' : 'text-emerald-600']">{{ t.tendencia }} vs. periodo anterior</div>
      </div>
    </div>

    <div v-if="datos" class="grid lg:grid-cols-2 gap-4">
      <div class="tarjeta p-4"><h3 class="font-semibold mb-2">Ticket promedio diario</h3><div class="h-64"><Line :data="ticket" :options="opciones" /></div></div>
      <div class="tarjeta p-4"><h3 class="font-semibold mb-2">Top 5 productos mas vendidos (unidades)</h3><div class="h-64"><Bar :data="top" :options="{ ...opciones, indexAxis: 'y' as const }" /></div></div>
      <div class="tarjeta p-4"><h3 class="font-semibold mb-2">Ventas por categoria</h3><div class="h-64"><Doughnut :data="categorias" :options="{ responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'right' as const } } }" /></div></div>
      <div class="tarjeta p-4"><h3 class="font-semibold mb-2">Rotacion de stock (vendido / stock actual)</h3><div class="h-64"><Bar :data="rotacion" :options="opciones" /></div></div>
    </div>

    <div v-if="datos" class="tarjeta overflow-x-auto">
      <h3 class="font-semibold px-4 pt-4">Margenes por categoria</h3>
      <table class="tabla w-full mt-2">
        <thead><tr><th>Categoria</th><th class="!text-right">Cant. vendida</th><th class="!text-right">Ingresos</th><th class="!text-right">Costo</th><th class="!text-right">Margen</th></tr></thead>
        <tbody>
          <tr v-if="!datos.categories.length"><td colspan="5" class="text-center text-slate-400">Sin ventas en el periodo</td></tr>
          <tr v-for="c in datos.categories" :key="c.name">
            <td class="font-medium">{{ c.name }}</td>
            <td class="text-right">{{ cantidad(c.qty) }}</td>
            <td class="text-right">{{ dinero(c.revenue) }}</td>
            <td class="text-right">{{ dinero(c.cost) }}</td>
            <td :class="['text-right font-semibold', c.margin < 15 ? 'text-rose-600' : 'text-emerald-700']">{{ c.margin }}%</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="cargando" class="text-center text-slate-400"><i class="fa-solid fa-spinner animate-spin"></i></p>
  </div>
</template>
