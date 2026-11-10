<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSesion } from '@/stores/sesion'
import { esNube } from '@/modo'

const sesion = useSesion()
const router = useRouter()
const route = useRoute()
const usuario = ref('')
const clave = ref('')
const error = ref('')
const enviando = ref(false)

async function ingresar() {
  error.value = ''
  enviando.value = true
  try {
    if (await sesion.ingresar(usuario.value.trim(), clave.value)) {
      const volver = typeof route.query.volver === 'string' ? route.query.volver : '/'
      router.replace(volver)
    } else {
      error.value = 'Usuario o clave incorrectos'
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'No se pudo ingresar'
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-slate-900 p-4">
    <form class="tarjeta w-full max-w-sm p-8 space-y-5" @submit.prevent="ingresar">
      <div class="text-center">
        <i class="fa-solid fa-store text-emerald-500 text-4xl"></i>
        <h1 class="text-2xl font-bold mt-2">Rey de Reyes</h1>
        <p class="text-sm text-slate-500">{{ esNube ? 'Acceso web del dueño' : 'Ingreso al sistema' }}</p>
      </div>
      <div>
        <label class="etiqueta" for="usuario">{{ esNube ? 'Email' : 'Usuario' }}</label>
        <input id="usuario" v-model="usuario" class="campo" autocomplete="username" autofocus required />
      </div>
      <div>
        <label class="etiqueta" for="clave">Clave</label>
        <input id="clave" v-model="clave" type="password" class="campo" autocomplete="current-password" required />
      </div>
      <p v-if="error" class="text-sm text-rose-600">{{ error }}</p>
      <button class="btn-primario w-full py-2.5" :disabled="enviando">
        <i class="fa-solid fa-right-to-bracket"></i> Ingresar
      </button>
    </form>
  </div>
</template>
