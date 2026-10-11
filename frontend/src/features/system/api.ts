import { queryOptions } from '@tanstack/react-query'
import { z } from 'zod'
import { apiRequest } from '@/lib/api/client'

/** Must match the backend's ServiceVerticalCode enum: a closed set of exactly six. */
export const BUSINESS_VERTICAL_CODES = [
  'CCTV_SECURITY',
  'DIGITAL_MARKETING',
  'INTERIOR_DESIGN',
  'ARCHITECTURE_TECH',
  'SOLAR',
  'IT_SUPPORT',
] as const

export const businessVerticalCodeSchema = z.enum(BUSINESS_VERTICAL_CODES)
export type BusinessVerticalCode = z.infer<typeof businessVerticalCodeSchema>

const businessVerticalSchema = z.object({
  code: businessVerticalCodeSchema,
  name: z.string(),
  displayOrder: z.number().int(),
})
export type BusinessVertical = z.infer<typeof businessVerticalSchema>

const systemInfoSchema = z.object({
  name: z.string(),
  version: z.string(),
  apiVersion: z.string(),
  serverTime: z.string(),
})
export type SystemInfo = z.infer<typeof systemInfoSchema>

export const systemInfoQuery = queryOptions({
  queryKey: ['system', 'info'],
  queryFn: ({ signal }) => apiRequest('/system/info', systemInfoSchema, { signal }),
})

export const businessVerticalsQuery = queryOptions({
  queryKey: ['service-verticals'],
  queryFn: ({ signal }) => apiRequest('/service-verticals', z.array(businessVerticalSchema), { signal }),
  // Reference data that only changes with a release.
  staleTime: 60 * 60 * 1000,
})
