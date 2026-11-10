<script setup lang="ts">
import { computed, ref } from 'vue'
import Modal from './Modal.vue'
import { tesoreriaApi } from '@/api/local'
import type { CierreCaja, ResumenTesoreria } from '@/api/tipos'
import { dinero } from '@/utils/formato'
import { useAvisos } from '@/stores/avisos'

const props = defineProps<{ resumen: ResumenTesoreria | null }>()
const emit = defineEmits<{ cerrar: []; cerrada: [CierreCaja] }>()
const avisos = useAvisos()

// Billetes y monedas de Argentina
const denominaciones = [20000, 10000, 2000, 1000, 500, 200, 100, 50, 20, 10, 5, 2, 1]
const cantidades = ref<Record<number, number | null>>(Object.fromEntries(denominaciones.map((d) => [d, null])))
const notas = ref('')
const enviando = ref(false)
const resultado = ref<CierreCaja | null>(null)

const contado = computed(() => denominaciones.reduce((acc, d) => acc + d * (Number(cantidades.value[d]) || 0), 0))
const esperado = computed(() => Number(props.resumen?.cashInDrawer ?? 0))
const diferencia = computed(() => contado.value - esperado.value)

async function confirmar() {
  enviando.value = true
  try {
    const bills = denominaciones
      .map((d) => ({ denomination: d, quantity: Number(cantidades.value[d]) || 0 }))
      .filter((b) => b.quantity > 0)
    if (!bills.length) bills.push({ denomination: 1, quantity: 0 })
    resultado.value = await tesoreriaApi.cerrarCaja(bills, notas.value)
    emit('cerrada', resultado.value)
  } catch (e) {
    avisos.error(e)
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <Modal titulo="Cierre de caja" ancho="max-w-2xl" :cerrable="!enviando" @cerrar="emit('cerrar')">
    <div v-if="!resultado" class="grid md:grid-cols-2 gap-6">
      <div>
        <p class="text-sm text-slate-600 mb-3">Contá el efectivo de la caja por billete y moneda.</p>
        <div class="grid grid-cols-2 gap-2">
          <label v-for="d in denominaciones" :key="d" class="flex items-center gap-2 text-sm">
            <span class="w-20 text-right font-medium text-slate-600">{{ dinero(d).replace(',00', '') }}</span>
            <input v-model.number="cantidades[d]" type="number" min="0" step="1" class="campo !py-1" :aria-label="`Cantidad de ${d}`" />
          </label>
        </div>
      </div>
      <div class="space-y-3">
        <div class="tarjeta p-4 bg-slate-50 space-y-2 text-sm">
          <div class="flex justify-between"><span>Efectivo esperado</span><b>{{ dinero(esperado) }}</b></div>
          <div class="flex justify-between"><span>Efectivo contado</span><b>{{ dinero(contado) }}</b></div>
          <div class="flex justify-between text-base" :class="diferencia < 0 ? 'text-rose-600' : diferencia > 0 ? 'text-amber-600' : 'text-emerald-600'">
            <span>Diferencia</span><b>{{ dinero(diferencia) }}</b>
          </div>
        </div>
        <div class="text-xs text-slate-500 space-y-1" v-if="resumen">
          <div class="flex justify-between"><span>Ventas</span><span>{{ dinero(resumen.sales) }}</span></div>
          <div class="flex justify-between"><span>Ventas por otros medios (no efectivo)</span><span>{{ dinero(resumen.transfersDebited) }}</span></div>
          <div class="flex justify-between"><span>Ingresos manuales</span><span>{{ dinero(resumen.incomes) }}</span></div>
          <div class="flex justify-between"><span>Egresos</span><span>{{ dinero(resumen.expenses) }}</span></div>
        </div>
        <div>
          <label class="etiqueta" for="cierre-notas">Observaciones</label>
          <textarea id="cierre-notas" v-model="notas" rows="2" class="campo"></textarea>
        </div>
        <p class="text-xs text-slate-500"><i class="fa-solid fa-cloud-arrow-up mr-1"></i>Al cerrar se suben los datos a la nube.</p>
      </div>
    </div>
    <div v-else class="space-y-3 text-sm">
      <p class="text-lg font-semibold text-emerald-700"><i class="fa-solid fa-circle-check mr-1"></i>Caja cerrada</p>
      <div class="flex justify-between"><span>Esperado</span><b>{{ dinero(resultado.expectedCash) }}</b></div>
      <div class="flex justify-between"><span>Contado</span><b>{{ dinero(resultado.countedCash) }}</b></div>
      <div class="flex justify-between"><span>Diferencia</span><b>{{ dinero(resultado.difference) }}</b></div>
      <p v-if="resultado.synced" class="p-3 rounded-lg bg-emerald-50 text-emerald-700"><i class="fa-solid fa-cloud mr-1"></i>Datos subidos a la nube.</p>
      <p v-else class="p-3 rounded-lg bg-rose-50 text-rose-700 font-medium">
        <i class="fa-solid fa-triangle-exclamation mr-1"></i>No se pudieron subir los datos a la nube.
        {{ resultado.syncMessage }}. Se reintentará automáticamente cuando haya conexión.
      </p>
    </div>
    <template #pie>
      <template v-if="!resultado">
        <button class="btn-secundario" :disabled="enviando" @click="emit('cerrar')">Cancelar</button>
        <button class="btn-peligro" :disabled="enviando" @click="confirmar">
          <i class="fa-solid fa-lock" :class="{ 'fa-spinner animate-spin': enviando }"></i> {{ enviando ? 'Cerrando y subiendo...' : 'Cerrar caja' }}
        </button>
      </template>
      <button v-else class="btn-primario" @click="emit('cerrar')">Listo</button>
    </template>
  </Modal>
</template>
