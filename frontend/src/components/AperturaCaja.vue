<script setup lang="ts">
import { ref } from 'vue'
import { tesoreriaApi } from '@/api/local'
import { useCaja } from '@/stores/caja'
import { useAvisos } from '@/stores/avisos'

const caja = useCaja()
const avisos = useAvisos()
const monto = ref<number | null>(null)
const notas = ref('')
const enviando = ref(false)

async function abrir() {
  if (monto.value === null || monto.value < 0) {
    avisos.mostrar('Ingrese el efectivo inicial (puede ser 0)', 'error')
    return
  }
  enviando.value = true
  try {
    await tesoreriaApi.abrirCaja(monto.value, notas.value)
    await caja.refrescar()
    avisos.exito('Caja abierta')
  } catch (e) {
    avisos.error(e)
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <form class="tarjeta p-5 border-amber-300 bg-amber-50/60 max-w-md" @submit.prevent="abrir">
    <h3 class="font-bold text-amber-800 flex items-center gap-2"><i class="fa-solid fa-lock"></i> La caja esta cerrada</h3>
    <p class="text-sm text-amber-700 mt-1 mb-4">Abrila para empezar a vender. Ingresá el efectivo con el que empieza el día.</p>
    <div class="grid grid-cols-2 gap-3">
      <div>
        <label class="etiqueta" for="apertura-monto">Efectivo inicial</label>
        <input id="apertura-monto" v-model.number="monto" type="number" min="0" step="0.01" class="campo" placeholder="0" />
      </div>
      <div>
        <label class="etiqueta" for="apertura-notas">Observaciones</label>
        <input id="apertura-notas" v-model="notas" class="campo" placeholder="Opcional" />
      </div>
    </div>
    <button class="btn-primario w-full mt-4" :disabled="enviando"><i class="fa-solid fa-lock-open"></i> Abrir caja</button>
  </form>
</template>
