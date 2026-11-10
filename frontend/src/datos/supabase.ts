import { createClient, type SupabaseClient } from '@supabase/supabase-js'

let cliente: SupabaseClient | null = null

/** Cliente de Supabase (solo en la web del duenio). */
export function supabase(): SupabaseClient {
  if (!cliente) {
    const url = import.meta.env.VITE_SUPABASE_URL
    const clave = import.meta.env.VITE_SUPABASE_ANON_KEY
    if (!url || !clave) {
      throw new Error('Faltan VITE_SUPABASE_URL y VITE_SUPABASE_ANON_KEY')
    }
    cliente = createClient(url, clave)
  }
  return cliente
}

export function verificar<T>(respuesta: { data: T | null; error: { message: string } | null }): T {
  if (respuesta.error) throw new Error(respuesta.error.message)
  return respuesta.data as T
}
