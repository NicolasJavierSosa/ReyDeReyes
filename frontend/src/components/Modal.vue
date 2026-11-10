<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

const props = withDefaults(defineProps<{ titulo: string; ancho?: string; cerrable?: boolean }>(), {
  ancho: 'max-w-lg',
  cerrable: true,
})
const emit = defineEmits<{ cerrar: [] }>()

function tecla(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.cerrable) emit('cerrar')
}
onMounted(() => window.addEventListener('keydown', tecla))
onBeforeUnmount(() => window.removeEventListener('keydown', tecla))
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-sm" @click="cerrable && emit('cerrar')"></div>
    <div class="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
      <div :class="['tarjeta w-full max-h-[92vh] flex flex-col pointer-events-auto shadow-2xl', ancho]" role="dialog" :aria-label="titulo">
        <div class="flex items-center justify-between px-5 py-3 border-b border-slate-200">
          <h3 class="text-lg font-bold text-slate-800"><slot name="titulo">{{ titulo }}</slot></h3>
          <button v-if="cerrable" type="button" class="text-slate-400 hover:text-slate-700" aria-label="Cerrar" @click="emit('cerrar')">
            <i class="fa-solid fa-xmark text-lg"></i>
          </button>
        </div>
        <div class="p-5 overflow-y-auto">
          <slot />
        </div>
        <div v-if="$slots.pie" class="px-5 py-3 border-t border-slate-200 flex justify-end gap-2 bg-slate-50 rounded-b-xl">
          <slot name="pie" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
