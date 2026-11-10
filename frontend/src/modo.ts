/** "local": PC del local (Spring Boot). "nube": web del duenio (Supabase). */
export const MODO: 'local' | 'nube' =
  import.meta.env.VITE_MODO === 'nube' || import.meta.env.MODE === 'nube' ? 'nube' : 'local'
export const esNube = MODO === 'nube'
