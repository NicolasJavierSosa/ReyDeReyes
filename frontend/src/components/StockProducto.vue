<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Modal from './Modal.vue'
import { productosApi } from '@/api/local'
import type { MovimientoStock, Producto } from '@/api/tipos'
import { useAvisos } from '@/stores/avisos'
import { cantidad, fechaHora } from '@/utils/formato'

const props = defineProps<{ producto: Producto }>()
const emit = defineEmits<{ cerrar: []; ajustado: [] }>()
const avisos = useAvisos()
const movimientos = ref<MovimientoStock[]>([])
const nuevoStock = ref<number>(props.producto.stock)
const motivo = ref('Recuento')
const guardando = ref(false)

const nombres: Record<MovimientoStock['type'], string> = {
  INICIAL: 'Stock inicial', VENTA: 'Venta', ANULACION: 'Anulacion', COMPRA: 'Compra', AJUSTE: 'Ajuste',
}

onMounted(async () => {
  try {
    movimientos.value = await productosApi.movimientos(props.producto.id)
  } catch (e) {
    avisos.error(e)
  }
})

async function ajustar() {
  guardando.value = true
  try {
    await productosApi.ajustarStock(props.producto.id, Number(nuevoStock.value), motivo.value)
    avisos.exito('Stock ajustado')
    emit('ajustado')
  } catch (e) {
    avisos.error(e)
  } finally {
    guardando.value = false
  }
}
</script>

<template>
  <Modal :titulo="`Stock: ${producto.name}`" ancho="max-w-2xl" @cerrar="emit('cerrar')">
    <form class="flex flex-wrap gap-3 items-end mb-5" @submit.prevent="ajustar">
      <div>
        <span class="etiqueta">Stock actual</span>
        <div class="text-2xl font-bold">{{ cantidad(producto.stock) }}</div>
      </div>
      <div class="w-32">
        <label class="etiqueta" for="s-nuevo">Stock real</label>
        <input id="s-nuevo" v-model.number="nuevoStock" type="number" min="0" step="any" class="campo" />
      </div>
      <div class="flex-1 min-w-40">
        <label class="etiqueta" for="s-motivo">Motivo</label>
        <input id="s-motivo" v-model="motivo" class="campo" />
      </div>
      <button class="btn-primario" :disabled="guardando || nuevoStock === producto.stock">Registrar ajuste</button>
    </form>
    <h4 class="font-semibold mb-2">Movimientos</h4>
    <table class="tabla w-full">
      <thead><tr><th>Fecha</th><th>Tipo</th><th>Detalle</th><th class="!text-right">Cantidad</th></tr></thead>
      <tbody>
        <tr v-if="!movimientos.length"><td colspan="4" class="text-center text-slate-400">Sin movimientos</td></tr>
        <tr v-for="m in movimientos" :key="m.id">
          <td class="whitespace-nowrap">{{ fechaHora(m.dateTime) }}</td>
          <td>{{ nombres[m.type] }}</td>
          <td class="text-slate-500">{{ m.reason ?? '' }}</td>
          <td :class="['text-right font-semibold', m.quantity < 0 ? 'text-rose-600' : 'text-emerald-700']">{{ m.quantity > 0 ? '+' : '' }}{{ cantidad(m.quantity) }}</td>
        </tr>
      </tbody>
    </table>
  </Modal>
</template>
