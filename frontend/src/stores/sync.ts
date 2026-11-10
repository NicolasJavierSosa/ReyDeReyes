import { defineStore } from 'pinia'
import { ref } from 'vue'
import { syncApi } from '@/api/local'
import type { EstadoSync, ResultadoSync } from '@/api/tipos'

export const useSync = defineStore('sync', () => {
  const estado = ref<EstadoSync | null>(null)
  const sincronizando = ref(false)
  let temporizador: number | undefined

  async function refrescar() {
    try {
      estado.value = await syncApi.estado()
    } catch {
      /* el indicador queda como estaba */
    }
  }

  async function ahora(): Promise<ResultadoSync> {
    sincronizando.value = true
    try {
      return await syncApi.ahora()
    } finally {
      sincronizando.value = false
      await refrescar()
    }
  }

  function iniciar() {
    refrescar()
    if (temporizador === undefined) {
      temporizador = window.setInterval(refrescar, 60_000)
    }
  }

  return { estado, sincronizando, refrescar, ahora, iniciar }
})
