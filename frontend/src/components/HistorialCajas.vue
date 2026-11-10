<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Modal from './Modal.vue'
import TablaMovimientos from './TablaMovimientos.vue'
import DetalleVenta from './DetalleVenta.vue'
import type { MovimientoTesoreria, Uuid } from '@/api/tipos'
import { consultas, type CajaResumen } from '@/datos/consultas'
import { useAvisos } from '@/stores/avisos'
import { dinero, fechaHora } from '@/utils/formato'

const avisos = useAvisos()
const desde = ref<string>('')
const hasta = ref<string>('')
const cajas = ref<CajaResumen[]>([])
const pagina = ref(0)
const hayMas = ref(false)
const cargando = ref(false)
const abierta = ref<CajaResumen | null>(null)
const movimientos = ref<MovimientoTesoreria[]>([])
const ventaId = ref<Uuid | null>(null)

async function cargar(reiniciar = true) {
  if (reiniciar) pagina.value = 0
  cargando.value = true
  try {
    const r = await consultas.cajas(desde.value || null, hasta.value || null, pagina.value)
    cajas.value = reiniciar ? r.items : [...cajas.value, ...r.items]
    hayMas.value = r.hayMas
  } catch (e) {
    avisos.error(e)
  } finally {
    cargando.value = false
  }
}
onMounted(() => cargar())

async function verMas() {
  pagina.value++
  await cargar(false)
}

async function verCaja(c: CajaResumen) {
  abierta.value = c
  movimientos.value = []
  try {
    movimientos.value = await consultas.movimientosDeCaja(c.id)
  } catch (e) {
    avisos.error(e)
  }
}

function totales(lista: MovimientoTesoreria[]) {
  const activos = lista.filter((m) => !m.voided)
  const suma = (f: (m: MovimientoTesoreria) => boolean) => activos.filter(f).reduce((acc, m) => acc + Math.abs(m.amount), 0)
  return {
    ventas: suma((m) => m.type === 'VENTA'),
    otrosMedios: suma((m) => m.type === 'VENTA' && !m.affectsCash),
    ingresos: suma((m) => m.type === 'INGRESO'),
    egresos: suma((m) => m.type === 'EGRESO'),
  }
}
</script>

<template>
  <div class="space-y-3">
    <form class="tarjeta p-3 flex flex-wrap gap-3 items-end" @submit.prevent="cargar()">
      <div><label class="etiqueta" for="h-desde">Desde</label><input id="h-desde" v-model="desde" type="date" class="campo" /></div>
      <div><label class="etiqueta" for="h-hasta">Hasta</label><input id="h-hasta" v-model="hasta" type="date" class="campo" /></div>
      <button class="btn-secundario"><i class="fa-solid fa-filter"></i> Filtrar</button>
    </form>
    <div class="tarjeta overflow-x-auto">
      <table class="tabla w-full">
        <thead><tr><th>Caja</th><th class="!text-right">Esperado</th><th class="!text-right">Contado</th><th class="!text-right">Diferencia</th><th>Observaciones</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!cargando && !cajas.length"><td colspan="6" class="text-center !py-6 text-slate-400">No hay cajas en ese periodo</td></tr>
          <tr v-for="c in cajas" :key="c.id">
            <td>
              <span v-if="c.open" class="insignia bg-emerald-100 text-emerald-700 mr-1">Abierta</span>
              <span class="whitespace-nowrap">{{ c.open ? `desde ${fechaHora(c.openedAt)}` : `cerrada ${fechaHora(c.closedAt)}` }}</span>
            </td>
            <td class="text-right">{{ c.expectedCash !== null ? dinero(c.expectedCash) : '-' }}</td>
            <td class="text-right">{{ c.countedCash !== null ? dinero(c.countedCash) : '-' }}</td>
            <td :class="['text-right font-semibold', (c.difference ?? 0) < 0 ? 'text-rose-600' : (c.difference ?? 0) > 0 ? 'text-amber-600' : '']">
              {{ c.difference !== null ? dinero(c.difference) : '-' }}
            </td>
            <td class="text-slate-500">{{ c.notes || '' }}</td>
            <td class="text-right"><button class="btn-secundario !py-1" @click="verCaja(c)">Ver detalle</button></td>
          </tr>
        </tbody>
      </table>
      <div v-if="hayMas" class="p-3 text-center"><button class="btn-secundario" :disabled="cargando" @click="verMas">Ver mas</button></div>
    </div>

    <Modal v-if="abierta" :titulo="abierta.open ? 'Caja abierta' : `Caja cerrada el ${fechaHora(abierta.closedAt)}`" ancho="max-w-4xl" @cerrar="abierta = null">
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4 text-sm">
        <div class="tarjeta p-3"><div class="text-slate-500">Ventas</div><b>{{ dinero(totales(movimientos).ventas) }}</b></div>
        <div class="tarjeta p-3"><div class="text-slate-500">Otros medios</div><b>{{ dinero(totales(movimientos).otrosMedios) }}</b></div>
        <div class="tarjeta p-3"><div class="text-slate-500">Ingresos</div><b>{{ dinero(totales(movimientos).ingresos) }}</b></div>
        <div class="tarjeta p-3"><div class="text-slate-500">Egresos</div><b>{{ dinero(totales(movimientos).egresos) }}</b></div>
      </div>
      <div class="tarjeta">
        <TablaMovimientos :movimientos="movimientos" con-fecha @ver-venta="ventaId = $event" />
      </div>
    </Modal>
    <DetalleVenta v-if="ventaId" :venta-id="ventaId" @cerrar="ventaId = null" />
  </div>
</template>
