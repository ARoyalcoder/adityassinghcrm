import { z } from 'zod'

/** Mirrors the backend's ApiErrorResponse (application/problem+json). See docs/api-conventions.md. */
export const apiProblemSchema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number(),
  code: z.string(),
  detail: z.string().optional(),
  instance: z.string().optional(),
  requestId: z.string().optional(),
  timestamp: z.string().optional(),
  errors: z.array(z.object({ field: z.string(), message: z.string() })).optional(),
})

export type ApiProblem = z.infer<typeof apiProblemSchema>
export type FieldViolation = { field: string; message: string }

/** Codes produced by the client itself, when there is no usable server error body. */
export const CLIENT_ERROR_CODES = {
  network: 'NETWORK_ERROR',
  unexpectedResponse: 'UNEXPECTED_RESPONSE',
} as const

export class ApiError extends Error {
  readonly status: number
  /** Machine-readable code; branch on this, never on the message. */
  readonly code: string
  /** Quote this to support; it matches the server log line. */
  readonly requestId: string | undefined
  readonly fieldErrors: FieldViolation[]

  constructor(args: {
    status: number
    code: string
    message: string
    requestId?: string
    fieldErrors?: FieldViolation[]
  }) {
    super(args.message)
    this.name = 'ApiError'
    this.status = args.status
    this.code = args.code
    this.requestId = args.requestId
    this.fieldErrors = args.fieldErrors ?? []
  }

  static fromProblem(problem: ApiProblem): ApiError {
    return new ApiError({
      status: problem.status,
      code: problem.code,
      message: problem.detail ?? problem.title ?? 'Request failed',
      requestId: problem.requestId,
      fieldErrors: problem.errors,
    })
  }
}
