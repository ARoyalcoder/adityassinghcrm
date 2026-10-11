import type { FieldValues, Path, UseFormReturn } from 'react-hook-form'
import { ApiError } from '@/lib/api/errors'

/**
 * Shows a failed save in the form: per-field problems reported by the server go next to their
 * fields, and anything else is returned as one message for the top of the form.
 */
export function applyServerErrors<T extends FieldValues>(form: UseFormReturn<T>, error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return 'Something went wrong. Please try again.'
  }
  const known = new Set(Object.keys(form.getValues()))
  let unplaced = false
  for (const violation of error.fieldErrors) {
    if (known.has(violation.field)) {
      form.setError(violation.field as Path<T>, { type: 'server', message: violation.message })
    } else {
      unplaced = true
    }
  }
  if (error.fieldErrors.length > 0 && !unplaced) {
    return null
  }
  return error.message
}

/** Empty text boxes are sent as null, never as "". */
export function blankToNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}
