import { defineStore } from 'pinia'
import { ref } from 'vue'
import { auth } from '@/api/local'
import { esNube } from '@/modo'
import { supabase } from '@/datos/supabase'

export const useSesion = defineStore('sesion', () => {
  const nombre = ref<string | null>(null)
  const usuario = ref<string | null>(null)
  const verificada = ref(false)

  async function verificar(): Promise<boolean> {
    try {
      if (esNube) {
        const { data } = await supabase().auth.getSession()
        usuario.value = data.session?.user.email ?? null
        nombre.value = usuario.value
      } else {
        const r = await fetch('/api/cuenta')
        if (r.ok) {
          const cuenta = await r.json()
          usuario.value = cuenta.username
          nombre.value = cuenta.name
        } else {
          usuario.value = null
        }
      }
    } catch {
      usuario.value = null
    }
    verificada.value = true
    return usuario.value !== null
  }

  async function ingresar(user: string, clave: string): Promise<boolean> {
    if (esNube) {
      const { error } = await supabase().auth.signInWithPassword({ email: user, password: clave })
      if (error) {
        const m = error.message.toLowerCase()
        if (m.includes('invalid login')) return false
        if (m.includes('not confirmed')) throw new Error('El usuario no esta confirmado. En Supabase: Authentication > Users > Confirm user.')
        if (m.includes('api key') || m.includes('apikey')) throw new Error('La clave publica de Supabase (VITE_SUPABASE_ANON_KEY) es incorrecta.')
        throw new Error(`No se pudo ingresar: ${error.message}`)
      }
    } else if (!(await auth.login(user, clave))) {
      return false
    }
    return verificar()
  }

  async function salir() {
    if (esNube) await supabase().auth.signOut()
    else await auth.logout()
    usuario.value = null
    nombre.value = null
  }

  return { nombre, usuario, verificada, verificar, ingresar, salir }
})
