<script setup lang="ts">
import HistorialCajas from '@/components/HistorialCajas.vue'
</script>

<template>
  <div class="p-4 lg:p-6 max-w-7xl mx-auto space-y-4">
    <h1 class="text-2xl font-bold">Cajas y tesoreria</h1>
    <p class="text-sm text-slate-600">Consulta de las cajas del local con sus ventas, ingresos y egresos. Solo lectura.</p>
    <HistorialCajas />
  </div>
</template>
