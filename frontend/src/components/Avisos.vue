<script setup lang="ts">
import { useAvisos } from '@/stores/avisos'
const avisos = useAvisos()
const estilos = {
  exito: 'bg-emerald-600',
  error: 'bg-rose-600',
  info: 'bg-slate-800',
}
const iconos = {
  exito: 'fa-circle-check',
  error: 'fa-triangle-exclamation',
  info: 'fa-circle-info',
}
</script>

<template>
  <div class="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 max-w-sm no-imprimir" aria-live="polite">
    <div v-for="a in avisos.lista" :key="a.id" :class="['text-white rounded-lg shadow-lg px-4 py-3 flex items-start gap-3 text-sm', estilos[a.tipo]]">
      <i :class="['fa-solid mt-0.5', iconos[a.tipo]]"></i>
      <span class="flex-1 whitespace-pre-line">{{ a.texto }}</span>
      <button class="opacity-70 hover:opacity-100" aria-label="Cerrar aviso" @click="avisos.cerrar(a.id)"><i class="fa-solid fa-xmark"></i></button>
    </div>
  </div>
</template>
