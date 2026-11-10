<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Avisos from './components/Avisos.vue'
import IndicadorSync from './components/IndicadorSync.vue'
import { useSesion } from './stores/sesion'
import { esNube } from './modo'

const route = useRoute()
const router = useRouter()
const sesion = useSesion()

const menu = esNube
  ? [
      { a: '/catalogo', icono: 'fa-boxes-stacked', texto: 'Catalogo' },
      { a: '/ventas', icono: 'fa-receipt', texto: 'Ventas' },
      { a: '/cajas', icono: 'fa-vault', texto: 'Cajas' },
    ]
  : [
      { a: '/venta', icono: 'fa-cart-shopping', texto: 'Venta' },
      { a: '/tesoreria', icono: 'fa-vault', texto: 'Tesoreria' },
      { a: '/inventario', icono: 'fa-boxes-stacked', texto: 'Inventario' },
      { a: '/compras', icono: 'fa-truck', texto: 'Compras' },
      { a: '/clientes', icono: 'fa-users', texto: 'Clientes' },
      { a: '/ventas', icono: 'fa-receipt', texto: 'Ventas' },
      { a: '/estadisticas', icono: 'fa-chart-pie', texto: 'Estadisticas' },
    ]

const conMenu = computed(() => route.name !== 'login')

async function salir() {
  await sesion.salir()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="min-h-screen flex flex-col text-slate-800">
    <header v-if="conMenu" class="bg-slate-900 text-white shadow-md no-imprimir">
      <div class="px-4 py-3 flex flex-wrap items-center gap-3">
        <div class="flex items-center gap-2 mr-2">
          <i class="fa-solid fa-store text-emerald-400 text-xl"></i>
          <span class="text-lg font-bold">Rey de Reyes</span>
          <span v-if="esNube" class="insignia bg-sky-500/20 text-sky-200">Web</span>
        </div>
        <nav class="flex flex-wrap gap-1 bg-slate-800 p-1 rounded-lg">
          <RouterLink
            v-for="m in menu"
            :key="m.a"
            :to="m.a"
            class="px-3 py-1.5 rounded-md text-sm font-medium text-slate-300 hover:text-white hover:bg-slate-700"
            active-class="!bg-emerald-500 !text-white"
          >
            <i :class="['fa-solid mr-1.5', m.icono]"></i>{{ m.texto }}
          </RouterLink>
        </nav>
        <div class="ml-auto flex items-center gap-2">
          <IndicadorSync v-if="!esNube" />
          <RouterLink v-if="!esNube" to="/conflictos" class="p-2 rounded-lg text-slate-300 hover:bg-slate-700" title="Conflictos de sincronizacion">
            <i class="fa-solid fa-code-merge"></i>
          </RouterLink>
          <RouterLink v-if="!esNube" to="/configuracion" class="p-2 rounded-lg text-slate-300 hover:bg-slate-700" title="Configuracion">
            <i class="fa-solid fa-gear"></i>
          </RouterLink>
          <button class="p-2 rounded-lg text-slate-300 hover:bg-slate-700" :title="`Salir (${sesion.nombre ?? ''})`" @click="salir">
            <i class="fa-solid fa-right-from-bracket"></i>
          </button>
        </div>
      </div>
    </header>
    <main class="flex-1">
      <RouterView />
    </main>
    <Avisos />
  </div>
</template>
