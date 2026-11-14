import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ventaDatos } from '@/datos/venta'
import type { EstadoCaja } from '@/api/tipos'

export const useCaja = defineStore('caja', () => {
  const estado = ref<EstadoCaja>({ open: false, id: null, openedAt: null, baseAmount: 0 })
  const cargada = ref(false)

  async function refrescar() {
    try {
      estado.value = await ventaDatos.estadoCaja()
    } finally {
      cargada.value = true
    }
  }

  return { estado, cargada, refrescar }
})
