<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MovimientoTesoreria, Uuid } from '@/api/tipos'
import { dinero, hora, normalizar } from '@/utils/formato'

const props = defineProps<{ movimientos: MovimientoTesoreria[]; editable?: boolean; conFecha?: boolean }>()
const emit = defineEmits<{ anular: [MovimientoTesoreria]; verVenta: [Uuid] }>()
const texto = ref('')
const tipo = ref<'todos' | 'VENTA' | 'INGRESO' | 'EGRESO'>('todos')
const verAnulados = ref(false)

const visibles = computed(() => {
  const t = normalizar(texto.value)
  return props.movimientos.filter((m) =>
    (tipo.value === 'todos' || m.type === tipo.value)
    && (verAnulados.value || !m.voided)
    && (!t || normalizar(`${m.concept} ${m.detail ?? ''} ${m.paymentMethod ?? ''}`).includes(t)))
})

const estilos = {
  VENTA: { clase: 'bg-emerald-100 text-emerald-700', texto: 'Venta' },
  INGRESO: { clase: 'bg-sky-100 text-sky-700', texto: 'Ingreso' },
  EGRESO: { clase: 'bg-rose-100 text-rose-700', texto: 'Egreso' },
}
</script>

<template>
  <div>
    <div class="flex flex-wrap gap-2 p-3 border-b border-slate-200 items-center">
      <input v-model="texto" class="campo !py-1.5 max-w-xs" placeholder="Buscar..." aria-label="Buscar movimientos" />
      <select v-model="tipo" class="campo !py-1.5 w-40" aria-label="Tipo de movimiento">
        <option value="todos">Todos los tipos</option><option value="VENTA">Ventas</option>
        <option value="INGRESO">Ingresos</option><option value="EGRESO">Egresos</option>
      </select>
      <label class="text-sm text-slate-600 flex items-center gap-1"><input v-model="verAnulados" type="checkbox" class="accent-emerald-600" /> Ver anulados</label>
    </div>
    <div class="overflow-x-auto">
      <table class="tabla w-full">
        <thead><tr><th>{{ conFecha ? 'Fecha' : 'Hora' }}</th><th>Tipo</th><th>Concepto</th><th>Medio</th><th class="!text-right">Monto</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!visibles.length"><td colspan="6" class="text-center !py-6 text-slate-400">Sin movimientos</td></tr>
          <tr v-for="m in visibles" :key="m.id" :class="{ 'opacity-50 line-through': m.voided }">
            <td class="whitespace-nowrap">{{ conFecha ? new Date(m.dateTime).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' }) : hora(m.dateTime) }}</td>
            <td><span :class="['insignia', estilos[m.type].clase]">{{ estilos[m.type].texto }}</span></td>
            <td>
              <div class="font-medium">{{ m.concept }}</div>
              <div v-if="m.detail" class="text-xs text-slate-500">{{ m.detail }}</div>
            </td>
            <td class="text-slate-500">{{ m.affectsCash ? 'Efectivo' : (m.paymentMethod ?? '-') }}</td>
            <td :class="['text-right font-semibold whitespace-nowrap', m.amount < 0 ? 'text-rose-600' : 'text-slate-800']">{{ dinero(m.amount) }}</td>
            <td class="text-right whitespace-nowrap">
              <button v-if="m.saleId" class="text-emerald-700 hover:underline text-xs font-semibold" @click="emit('verVenta', m.saleId)">Ver ticket</button>
              <button v-else-if="editable && m.deletable && !m.voided" class="text-rose-500 hover:text-rose-700 p-1" title="Anular" @click="emit('anular', m)"><i class="fa-solid fa-ban"></i></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
