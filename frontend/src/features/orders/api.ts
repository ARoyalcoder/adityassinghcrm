import { keepPreviousData, queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'
import { pageSchema, queryString } from '@/lib/api/page'

export const ORDER_STATUSES = [
  { value: 'CONFIRMED', label: 'Confirmed', description: 'Order confirmed and registered' },
  { value: 'IN_PROGRESS', label: 'In Progress', description: 'Procurement / fulfillment underway' },
  { value: 'PARTIALLY_DELIVERED', label: 'Partially Delivered', description: 'Partial items or milestones delivered' },
  { value: 'COMPLETED', label: 'Completed', description: 'Fully delivered and fulfilled' },
  { value: 'CANCELLED', label: 'Cancelled', description: 'Order cancelled' },
] as const

export type OrderStatus = (typeof ORDER_STATUSES)[number]['value']

export const orderItemSchema = z.object({
  id: z.string(),
  itemOrder: z.number(),
  serviceId: z.string().nullish(),
  serviceName: z.string(),
  description: z.string().nullish(),
  quantity: z.number(),
  unitPrice: z.number(),
  discountPercentage: z.number().nullish(),
  discountAmount: z.number(),
  taxPercentage: z.number().nullish(),
  taxAmount: z.number(),
  subtotal: z.number(),
  totalAmount: z.number(),
  deliveredQuantity: z.number(),
})
export type OrderItem = z.infer<typeof orderItemSchema>

export const orderHistorySchema = z.object({
  id: z.string(),
  action: z.string(),
  fromStatus: z.string().nullish(),
  toStatus: z.string().nullish(),
  notes: z.string().nullish(),
  performedBy: z.string(),
  performedAt: z.string(),
})
export type OrderHistory = z.infer<typeof orderHistorySchema>

export const orderSummarySchema = z.object({
  id: z.string(),
  orderNumber: z.string(),
  title: z.string(),
  customerId: z.string(),
  customerName: z.string(),
  quotationId: z.string().nullish(),
  quotationNumber: z.string().nullish(),
  quotationVersionNumber: z.number().nullish(),
  status: z.enum(['CONFIRMED', 'IN_PROGRESS', 'PARTIALLY_DELIVERED', 'COMPLETED', 'CANCELLED']),
  subtotal: z.number(),
  discountAmount: z.number(),
  taxAmount: z.number(),
  finalAmount: z.number(),
  ownerId: z.string(),
  ownerName: z.string(),
  deliveryDate: z.string().nullish(),
  itemCount: z.number(),
  createdAt: z.string(),
})
export type OrderSummary = z.infer<typeof orderSummarySchema>

export const orderDetailSchema = z.object({
  id: z.string(),
  orderNumber: z.string(),
  title: z.string(),
  customerId: z.string(),
  customerName: z.string(),
  quotationId: z.string().nullish(),
  quotationNumber: z.string().nullish(),
  quotationVersionNumber: z.number().nullish(),
  status: z.enum(['CONFIRMED', 'IN_PROGRESS', 'PARTIALLY_DELIVERED', 'COMPLETED', 'CANCELLED']),
  subtotal: z.number(),
  discountAmount: z.number(),
  taxAmount: z.number(),
  finalAmount: z.number(),
  ownerId: z.string(),
  ownerName: z.string(),
  deliveryDate: z.string().nullish(),
  notes: z.string().nullish(),
  terms: z.string().nullish(),
  cancellationReason: z.string().nullish(),
  cancelledAt: z.string().nullish(),
  cancelledBy: z.string().nullish(),
  completedAt: z.string().nullish(),
  completedBy: z.string().nullish(),
  createdAt: z.string(),
  createdBy: z.string(),
  updatedAt: z.string(),
  updatedBy: z.string(),
  items: z.array(orderItemSchema),
  history: z.array(orderHistorySchema),
})
export type OrderDetail = z.infer<typeof orderDetailSchema>

export const orderDashboardSchema = z.object({
  totalOrders: z.number(),
  totalValue: z.number(),
  confirmedCount: z.number(),
  inProgressCount: z.number(),
  partiallyDeliveredCount: z.number(),
  completedCount: z.number(),
  cancelledCount: z.number(),
  recentOrders: z.array(orderSummarySchema),
})
export type OrderDashboard = z.infer<typeof orderDashboardSchema>

export type OrderListParams = {
  status?: OrderStatus
  customerId?: string
  ownerId?: string
  search?: string
  page?: number
  size?: number
  sort?: string
}

export type ConvertQuotationPayload = {
  quotationId: string
  title?: string
  ownerId?: string
  deliveryDate?: string
  notes?: string
}

export type CreateDirectOrderPayload = {
  title: string
  customerId: string
  customerName: string
  ownerId?: string
  deliveryDate?: string
  notes?: string
  terms?: string
  items: {
    serviceId?: string
    serviceName: string
    description?: string
    quantity: number
    unitPrice: number
    discountPercentage?: number
    taxPercentage?: number
  }[]
}

export type UpdateOrderStatusPayload = {
  status: OrderStatus
  reason?: string
  notes?: string
}

export type UpdateOrderDetailsPayload = {
  title?: string
  deliveryDate?: string
  notes?: string
  terms?: string
}

export const orderKeys = {
  all: ['orders'] as const,
  lists: () => [...orderKeys.all, 'list'] as const,
  list: (params: OrderListParams) => [...orderKeys.lists(), params] as const,
  details: () => [...orderKeys.all, 'detail'] as const,
  detail: (id: string) => [...orderKeys.details(), id] as const,
  dashboard: () => [...orderKeys.all, 'dashboard'] as const,
}

export function ordersQuery(params: OrderListParams = {}) {
  const query = queryString({
    status: params.status,
    customerId: params.customerId,
    ownerId: params.ownerId,
    search: params.search,
    page: params.page ?? 0,
    size: params.size ?? 20,
    sort: params.sort,
  })

  return queryOptions({
    queryKey: orderKeys.list(params),
    queryFn: () =>
      apiRequest(`/api/v1/orders${query}`, pageSchema(orderSummarySchema)),
    placeholderData: keepPreviousData,
  })
}

export function orderDetailQuery(id: string) {
  return queryOptions({
    queryKey: orderKeys.detail(id),
    queryFn: () => apiRequest(`/api/v1/orders/${id}`, orderDetailSchema),
  })
}

export function orderDashboardQuery() {
  return queryOptions({
    queryKey: orderKeys.dashboard(),
    queryFn: () => apiRequest('/api/v1/orders/dashboard', orderDashboardSchema),
  })
}

export async function convertQuotationToOrder(data: ConvertQuotationPayload): Promise<OrderDetail> {
  return apiRequest('/api/v1/orders/from-quotation', orderDetailSchema, {
    method: 'POST',
    body: data,
  })
}

export async function createDirectOrder(data: CreateDirectOrderPayload): Promise<OrderDetail> {
  return apiRequest('/api/v1/orders', orderDetailSchema, {
    method: 'POST',
    body: data,
  })
}

export async function updateOrderStatus(orderId: string, data: UpdateOrderStatusPayload): Promise<OrderDetail> {
  return apiRequest(`/api/v1/orders/${orderId}/status`, orderDetailSchema, {
    method: 'PATCH',
    body: data,
  })
}

export async function updateOrderDetails(orderId: string, data: UpdateOrderDetailsPayload): Promise<OrderDetail> {
  return apiRequest(`/api/v1/orders/${orderId}/details`, orderDetailSchema, {
    method: 'PATCH',
    body: data,
  })
}

export async function updateItemDelivery(
  orderId: string,
  itemId: string,
  deliveredQuantity: number
): Promise<OrderDetail> {
  return apiRequest(`/api/v1/orders/${orderId}/items/${itemId}/delivery`, orderDetailSchema, {
    method: 'PATCH',
    body: { deliveredQuantity },
  })
}
