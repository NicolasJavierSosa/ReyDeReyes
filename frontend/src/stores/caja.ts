import { defineStore } from 'pinia'
import { ref } from 'vue'
import { tesoreriaApi } from '@/api/local'
import type { EstadoCaja } from '@/api/tipos'

export const useCaja = defineStore('caja', () => {
  const estado = ref<EstadoCaja>({ open: false, id: null, openedAt: null, baseAmount: 0 })
  const cargada = ref(false)

  async function refrescar() {
    try {
      estado.value = await tesoreriaApi.estadoCaja()
    } finally {
      cargada.value = true
    }
  }

  return { estado, cargada, refrescar }
})
