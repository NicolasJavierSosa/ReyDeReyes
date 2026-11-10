import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Aviso {
  id: number
  tipo: 'exito' | 'error' | 'info'
  texto: string
}

let siguiente = 1

/** Mensajes breves en la esquina de la pantalla. */
export const useAvisos = defineStore('avisos', () => {
  const lista = ref<Aviso[]>([])

  function mostrar(texto: string, tipo: Aviso['tipo'] = 'info', ms = 4000) {
    const id = siguiente++
    lista.value.push({ id, tipo, texto })
    setTimeout(() => cerrar(id), ms)
  }

  function cerrar(id: number) {
    lista.value = lista.value.filter((a) => a.id !== id)
  }

  const exito = (texto: string) => mostrar(texto, 'exito')
  const error = (e: unknown) => mostrar(e instanceof Error ? e.message : String(e), 'error', 7000)

  return { lista, mostrar, cerrar, exito, error }
})
