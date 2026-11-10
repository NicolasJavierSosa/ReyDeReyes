<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import Modal from '@/components/Modal.vue'
import AperturaCaja from '@/components/AperturaCaja.vue'
import CierreCaja from '@/components/CierreCaja.vue'
import TablaMovimientos from '@/components/TablaMovimientos.vue'
import HistorialCajas from '@/components/HistorialCajas.vue'
import DetalleVenta from '@/components/DetalleVenta.vue'
import { tesoreriaApi } from '@/api/local'
import type { MovimientoTesoreria, ResumenTesoreria, Uuid } from '@/api/tipos'
import { useCaja } from '@/stores/caja'
import { useAvisos } from '@/stores/avisos'
import { useSync } from '@/stores/sync'
import { dinero, fechaHora } from '@/utils/formato'

const caja = useCaja()
const avisos = useAvisos()
const sync = useSync()
const pestana = ref<'actual' | 'historial'>('actual')
const resumen = ref<ResumenTesoreria | null>(null)
const movimientos = ref<MovimientoTesoreria[]>([])
const cerrando = ref(false)
const ventaId = ref<Uuid | null>(null)

const conceptos = {
  INGRESO: ['Aporte del dueño', 'Cobro de cuenta corriente', 'Otros ingresos'],
  EGRESO: ['Pago a proveedor', 'Retiro del dueño', 'Insumos de limpieza / libreria', 'Pago de servicios', 'Otros gastos'],
}
const nuevo = ref<{ type: 'INGRESO' | 'EGRESO'; concept: string; detail: string; amount: number | null } | null>(null)

async function cargar() {
  await caja.refrescar()
  if (!caja.estado.open) {
    resumen.value = null
    movimientos.value = []
    return
  }
  try {
    const [r, m] = await Promise.all([tesoreriaApi.resumen(), tesoreriaApi.movimientos()])
    resumen.value = r
    movimientos.value = m
  } catch (e) {
    avisos.error(e)
  }
}
onMounted(cargar)
watch(() => caja.estado.open, (abierta, antes) => { if (abierta && !antes) cargar() })

function nuevoMovimiento(type: 'INGRESO' | 'EGRESO') {
  nuevo.value = { type, concept: conceptos[type][0], detail: '', amount: null }
}

async function guardarMovimiento() {
  const n = nuevo.value
  if (!n || !n.amount || n.amount <= 0) return avisos.mostrar('Ingrese un monto valido', 'error')
  try {
    await tesoreriaApi.registrarManual({ type: n.type, concept: n.concept, detail: n.detail, amount: n.amount })
    avisos.exito(n.type === 'INGRESO' ? 'Ingreso registrado' : 'Egreso registrado')
    nuevo.value = null
    await cargar()
  } catch (e) {
    avisos.error(e)
  }
}

async function anular(m: MovimientoTesoreria) {
  if (!confirm(`¿Anular "${m.concept}" por ${dinero(m.amount)}? Queda registrado como anulado.`)) return
  try {
    await tesoreriaApi.anularMovimiento(m.id)
    await cargar()
  } catch (e) {
    avisos.error(e)
  }
}

async function cajaCerrada() {
  await caja.refrescar()
  sync.refrescar()
}

function cerrarModalCierre() {
  cerrando.value = false
  cargar()
}
</script>

<template>
  <div class="p-4 lg:p-6 max-w-7xl mx-auto space-y-4">
    <div class="flex flex-wrap items-center gap-2">
      <h1 class="text-2xl font-bold mr-auto">Tesoreria</h1>
      <div class="flex bg-slate-200 rounded-lg p-1">
        <button :class="['px-3 py-1.5 rounded-md text-sm font-medium', pestana === 'actual' ? 'bg-white shadow' : 'text-slate-600']" @click="pestana = 'actual'">Caja actual</button>
        <button :class="['px-3 py-1.5 rounded-md text-sm font-medium', pestana === 'historial' ? 'bg-white shadow' : 'text-slate-600']" @click="pestana = 'historial'">Historial de cajas</button>
      </div>
    </div>

    <template v-if="pestana === 'actual'">
      <AperturaCaja v-if="caja.cargada && !caja.estado.open" />
      <template v-else-if="caja.estado.open">
        <div class="tarjeta p-4 flex flex-wrap items-center gap-3">
          <i class="fa-solid fa-cash-register text-2xl text-emerald-600"></i>
          <div class="mr-auto">
            <div class="font-semibold">Caja abierta desde {{ fechaHora(caja.estado.openedAt) }}</div>
            <div class="text-sm text-slate-500">Efectivo inicial: {{ dinero(caja.estado.baseAmount) }}</div>
          </div>
          <button class="btn-secundario" @click="nuevoMovimiento('INGRESO')"><i class="fa-solid fa-arrow-down text-sky-600"></i> Ingreso</button>
          <button class="btn-secundario" @click="nuevoMovimiento('EGRESO')"><i class="fa-solid fa-arrow-up text-rose-600"></i> Retiro / gasto</button>
          <button class="btn-peligro" @click="cerrando = true"><i class="fa-solid fa-lock"></i> Cerrar caja</button>
        </div>

        <div v-if="resumen" class="grid grid-cols-2 lg:grid-cols-5 gap-3">
          <div class="tarjeta p-4"><div class="text-xs text-slate-500 uppercase">Ventas</div><div class="text-xl font-bold">{{ dinero(resumen.sales) }}</div></div>
          <div class="tarjeta p-4"><div class="text-xs text-slate-500 uppercase">Otros medios</div><div class="text-xl font-bold">{{ dinero(resumen.transfersDebited) }}</div></div>
          <div class="tarjeta p-4"><div class="text-xs text-slate-500 uppercase">Ingresos</div><div class="text-xl font-bold text-sky-700">{{ dinero(resumen.incomes) }}</div></div>
          <div class="tarjeta p-4"><div class="text-xs text-slate-500 uppercase">Egresos</div><div class="text-xl font-bold text-rose-600">{{ dinero(resumen.expenses) }}</div></div>
          <div class="tarjeta p-4 bg-slate-900 text-white border-slate-900"><div class="text-xs text-slate-400 uppercase">Efectivo en caja</div><div class="text-xl font-bold text-emerald-400">{{ dinero(resumen.cashInDrawer) }}</div></div>
        </div>

        <div class="tarjeta">
          <TablaMovimientos :movimientos="movimientos" editable @anular="anular" @ver-venta="ventaId = $event" />
        </div>
      </template>
    </template>

    <HistorialCajas v-else />

    <Modal v-if="nuevo" :titulo="nuevo.type === 'INGRESO' ? 'Registrar ingreso' : 'Registrar retiro o gasto'" @cerrar="nuevo = null">
      <form id="form-mov" class="space-y-3" @submit.prevent="guardarMovimiento">
        <div>
          <label class="etiqueta" for="m-concepto">Concepto *</label>
          <select id="m-concepto" v-model="nuevo.concept" class="campo">
            <option v-for="c in conceptos[nuevo.type]" :key="c" :value="c">{{ c }}</option>
          </select>
        </div>
        <div>
          <label class="etiqueta" for="m-monto">Monto *</label>
          <input id="m-monto" v-model.number="nuevo.amount" type="number" min="0.01" step="0.01" class="campo" required autofocus />
        </div>
        <div>
          <label class="etiqueta" for="m-detalle">Detalle</label>
          <input id="m-detalle" v-model="nuevo.detail" class="campo" placeholder="Ej: pago a distribuidora de bebidas" />
        </div>
        <p class="text-xs text-slate-500">Se registra como efectivo de la caja.</p>
      </form>
      <template #pie>
        <button class="btn-secundario" @click="nuevo = null">Cancelar</button>
        <button class="btn-primario" form="form-mov">Registrar</button>
      </template>
    </Modal>

    <CierreCaja v-if="cerrando" :resumen="resumen" @cerrar="cerrarModalCierre" @cerrada="cajaCerrada" />
    <DetalleVenta v-if="ventaId" :venta-id="ventaId" @cerrar="ventaId = null" @anulada="cargar" />
  </div>
</template>
