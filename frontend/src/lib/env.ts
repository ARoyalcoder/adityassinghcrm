import { z } from 'zod'

const schema = z.object({
  VITE_API_BASE_URL: z.string().min(1).default('/api/v1'),
})

// Fails at startup (not at first request) if the build was configured incorrectly.
const parsed = schema.parse(import.meta.env)

export const env = {
  apiBaseUrl: parsed.VITE_API_BASE_URL.replace(/\/+$/, ''),
} as const
