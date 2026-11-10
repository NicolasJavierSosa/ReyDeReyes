<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Modal from './Modal.vue'
import { ventasApi } from '@/api/local'
import type { Uuid, VentaDetalle } from '@/api/tipos'
import { consultas } from '@/datos/consultas'
import { cantidad, dinero, fechaHora } from '@/utils/formato'
import { useAvisos } from '@/stores/avisos'
import { esNube } from '@/modo'

const props = defineProps<{ ventaId: Uuid }>()
const emit = defineEmits<{ cerrar: []; anulada: [] }>()
const avisos = useAvisos()
const venta = ref<VentaDetalle | null>(null)
const anulando = ref(false)

onMounted(async () => {
  try {
    venta.value = await consultas.ventaDetalle(props.ventaId)
  } catch (e) {
    avisos.error(e)
    emit('cerrar')
  }
})

async function anular() {
  if (!venta.value || !confirm(`¿Anular la venta #${venta.value.ticket}? Se devuelve el stock y se descuenta de la caja.`)) return
  anulando.value = true
  try {
    await ventasApi.anular(props.ventaId)
    avisos.exito(`Venta #${venta.value.ticket} anulada`)
    emit('anulada')
    emit('cerrar')
  } catch (e) {
    avisos.error(e)
  } finally {
    anulando.value = false
  }
}
</script>

<template>
  <Modal :titulo="venta ? `Venta #${venta.ticket}` : 'Venta'" ancho="max-w-2xl" @cerrar="emit('cerrar')">
    <div v-if="!venta" class="text-center text-slate-500 py-6"><i class="fa-solid fa-spinner animate-spin"></i></div>
    <div v-else class="space-y-4">
      <div class="flex flex-wrap gap-4 text-sm text-slate-600">
        <span><i class="fa-regular fa-clock mr-1"></i>{{ fechaHora(venta.dateTime) }}</span>
        <span><i class="fa-solid fa-wallet mr-1"></i>{{ venta.paymentMethod ?? '-' }}</span>
        <span :class="['insignia', venta.status === 'ANULADA' ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-700']">{{ venta.status }}</span>
      </div>
      <table class="tabla w-full">
        <thead><tr><th>Producto</th><th class="!text-right">Cant.</th><th class="!text-right">Precio</th><th class="!text-right">Total</th></tr></thead>
        <tbody>
          <tr v-for="(i, n) in venta.items" :key="n">
            <td>{{ i.name }}<div class="text-xs text-slate-400">{{ i.barcode }}</div></td>
            <td class="text-right">{{ cantidad(i.quantity) }}</td>
            <td class="text-right">{{ dinero(i.unitPrice) }}</td>
            <td class="text-right">{{ dinero(i.totalLine) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="ml-auto max-w-xs space-y-1 text-sm">
        <div class="flex justify-between"><span>Subtotal</span><span>{{ dinero(venta.subtotal) }}</span></div>
        <div class="flex justify-between text-emerald-700"><span>Descuento</span><span>-{{ dinero(venta.discount) }}</span></div>
        <div class="flex justify-between text-lg font-bold"><span>Total</span><span>{{ dinero(venta.total) }}</span></div>
      </div>
    </div>
    <template #pie>
      <button v-if="!esNube && venta?.status === 'COMPLETADA'" class="btn-peligro mr-auto" :disabled="anulando" @click="anular">
        <i class="fa-solid fa-ban"></i> Anular venta
      </button>
      <button class="btn-secundario" @click="emit('cerrar')">Cerrar</button>
    </template>
  </Modal>
</template>
