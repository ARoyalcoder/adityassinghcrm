import { z } from 'zod'

/** Mirrors the backend's PageResponse: the body of every paginated list endpoint. */
export function pageSchema<T extends z.ZodTypeAny>(item: T) {
  return z.object({
    items: z.array(item),
    page: z.number(),
    size: z.number(),
    totalItems: z.number(),
    totalPages: z.number(),
  })
}

export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

/** Builds "?a=1&b=2", leaving out empty values so "no filter" never reaches the server as an empty string. */
export function queryString(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}
