import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { esNube } from './modo'

const rutasLocal: RouteRecordRaw[] = [
  { path: '/', redirect: '/venta' },
  { path: '/venta', name: 'venta', component: () => import('./views/VentaView.vue'), meta: { titulo: 'Venta' } },
  { path: '/inventario', name: 'inventario', component: () => import('./views/InventarioView.vue'), meta: { titulo: 'Inventario' } },
  { path: '/tesoreria', name: 'tesoreria', component: () => import('./views/TesoreriaView.vue'), meta: { titulo: 'Tesoreria' } },
  { path: '/ventas', name: 'ventas', component: () => import('./views/VentasView.vue'), meta: { titulo: 'Ventas' } },
  { path: '/compras', name: 'compras', component: () => import('./views/ComprasView.vue'), meta: { titulo: 'Compras' } },
  { path: '/clientes', name: 'clientes', component: () => import('./views/ClientesView.vue'), meta: { titulo: 'Clientes' } },
  { path: '/estadisticas', name: 'estadisticas', component: () => import('./views/EstadisticasView.vue'), meta: { titulo: 'Estadisticas' } },
  { path: '/conflictos', name: 'conflictos', component: () => import('./views/ConflictosView.vue'), meta: { titulo: 'Conflictos' } },
  { path: '/configuracion', name: 'configuracion', component: () => import('./views/ConfiguracionView.vue'), meta: { titulo: 'Configuracion' } },
]

// Web del duenio: catalogo editable y consultas en solo lectura
const rutasNube: RouteRecordRaw[] = [
  { path: '/', redirect: '/catalogo' },
  { path: '/venta', name: 'venta', component: () => import('./views/VentaView.vue'), meta: { titulo: 'Venta (contingencia)' } },
  { path: '/catalogo', name: 'catalogo', component: () => import('./views/InventarioView.vue'), meta: { titulo: 'Catalogo' } },
  { path: '/ventas', name: 'ventas', component: () => import('./views/VentasView.vue'), meta: { titulo: 'Ventas' } },
  { path: '/cajas', name: 'cajas', component: () => import('./views/CajasView.vue'), meta: { titulo: 'Cajas y tesoreria' } },
]

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { publica: true } },
    ...(esNube ? rutasNube : rutasLocal),
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const { useSesion } = await import('./stores/sesion')
  const sesion = useSesion()
  if (!sesion.verificada) {
    await sesion.verificar()
  }
  if (!to.meta.publica && !sesion.usuario) {
    return { name: 'login', query: { volver: to.fullPath } }
  }
  if (to.name === 'login' && sesion.usuario) {
    return '/'
  }
  return true
})

router.afterEach((to) => {
  const titulo = to.meta.titulo as string | undefined
  document.title = titulo ? `${titulo} · Rey de Reyes` : 'Rey de Reyes'
})
