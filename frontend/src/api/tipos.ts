// Tipos de los datos que devuelve la API (mismos nombres que los DTO de Spring)

export type Uuid = string

export interface Categoria {
  id: Uuid
  nombre: string
  activa: boolean
}

export interface Producto {
  id: Uuid
  barcode: string
  name: string
  description: string | null
  categoryId: Uuid | null
  categoryName: string | null
  supplierId: string | null
  supplierName: string | null
  unit: string | null
  type: string | null
  cost: number
  price: number
  stock: number
  minStock: number
  active: boolean
  combo: boolean
  comboItems: Uuid[]
  comboGroupCategoryId: Uuid | null
  comboGroupCategoryName: string | null
  comboGroupQuantity: number | null
}

export interface ProductoRequest {
  barcode: string
  name: string
  description: string
  categoryId: Uuid | null
  supplierId: string | null
  unit: string
  type: string
  cost: number
  price: number
  stock: number | null
  stockOriginal: number | null
  minStock: number
  active: boolean
}

export interface ComboRequest {
  barcode: string
  name: string
  description: string
  categoryId: Uuid | null
  unit: string
  type: string
  cost: number
  price: number
  active: boolean
  items: Uuid[] | null
  groupCategoryId: Uuid | null
  groupQuantity: number | null
}

export interface ProductoVenta {
  id: Uuid
  barcode: string
  name: string
  price: number
  categoryId: Uuid | null
  categoryName: string | null
}

export interface MovimientoStock {
  id: Uuid
  dateTime: string
  type: 'INICIAL' | 'VENTA' | 'ANULACION' | 'COMPRA' | 'AJUSTE'
  quantity: number
  reason: string | null
  saleNumber: number | null
}

export interface Proveedor {
  phone: string
  name: string
  active: boolean
}

export interface Cliente {
  id: Uuid
  firstName: string
  lastName: string
  phone: string
  email: string
  active: boolean
}

export interface MetodoPago {
  id: Uuid
  code: string
  name: string
  affectsCash: boolean
  active: boolean
  order: number
}

export interface EstadoCaja {
  open: boolean
  id: Uuid | null
  openedAt: string | null
  baseAmount: number
}

export interface CierreCaja {
  expectedCash: number
  countedCash: number
  difference: number
  closedAt: string
  synced: boolean
  syncMessage: string | null
}

export interface CajaCerrada {
  id: Uuid
  closedAt: string
  expectedCash: number
  countedCash: number
  difference: number
  responsible: string
  notes: string
}

export interface MovimientoTesoreria {
  id: Uuid
  dateTime: string
  type: 'INGRESO' | 'EGRESO' | 'VENTA'
  concept: string
  detail: string | null
  user: string | null
  amount: number
  paymentMethod: string | null
  saleId: Uuid | null
  affectsCash: boolean
  deletable: boolean
  voided: boolean
}

export interface ResumenTesoreria {
  incomes: number
  sales: number
  expenses: number
  cashInDrawer: number
  transfersDebited: number
}

export interface VentaCheckoutRespuesta {
  saleId: Uuid
  ticket: string
  subtotal: number
  discount: number
  total: number
  paymentMethod: string
  amountReceived: number | null
  change: number
  dateTime: string
}

export interface VentaDetalle {
  id: Uuid
  ticket: string
  dateTime: string
  subtotal: number
  discount: number
  total: number
  status: 'COMPLETADA' | 'ANULADA'
  paymentMethod: string | null
  items: { barcode: string; name: string; quantity: number; unitPrice: number; subtotal: number; totalLine: number }[]
}

export interface VentaResumen {
  id: Uuid
  ticket: string
  dateTime: string
  total: number
  status: 'COMPLETADA' | 'ANULADA'
  paymentMethod: string | null
  items: number
}

export interface SolicitudCompra {
  id: Uuid
  supplierId: string
  supplierName: string
  status: 'SOLICITADA' | 'PARCIAL' | 'RECIBIDA'
  dateTime: string
  items: { id: Uuid; productId: Uuid; productName: string; qtyRequested: number; qtyReceived: number }[]
}

export interface Estadisticas {
  period: string
  from: string
  to: string
  kpis: {
    revenue: number
    profit: number
    items: number
    salesCount: number
    trends: { rev: string; prof: string; items: string; salesCount: string }
  }
  ticketPromedio: Serie
  topProducts: Serie
  rotation: Serie
  categories: { name: string; qty: number; revenue: number; cost: number; margin: number }[]
}

export interface Serie {
  labels: string[]
  data: number[]
}

export interface EstadoSync {
  level: 'VERDE' | 'AMARILLO' | 'ROJO' | 'DESACTIVADA'
  enabled: boolean
  pendingChanges: number
  unreviewedConflicts: number
  lastSuccess: string | null
  lastAttempt: string | null
  lastError: string | null
}

export interface ResultadoSync {
  ok: boolean
  pushed: number
  pulled: number
  conflicts: number
  message: string
}

export interface Conflicto {
  id: Uuid
  table: string
  entityId: string
  winner: 'LOCAL' | 'NUBE' | 'FUSION'
  winnerData: string | null
  loserData: string | null
  detectedAt: string
  reviewed: boolean
}

export interface OpenFoodFacts {
  found: boolean
  barcode: string
  name: string | null
  description: string | null
  brand: string | null
  quantity: string | null
  categories: string | null
  imageUrl: string | null
  reason: string | null
}
