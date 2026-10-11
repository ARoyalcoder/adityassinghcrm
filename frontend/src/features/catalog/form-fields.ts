import { z } from 'zod'

/**
 * Field rules shared by the catalog and branch forms. They repeat the server's rules so mistakes
 * are caught while typing; the server checks again and has the final word.
 */
export const codeField = (max: number) =>
  z
    .string()
    .trim()
    .min(1, 'Enter a code')
    .max(max, `At most ${max} characters`)
    .regex(/^[A-Z][A-Z0-9_]*$/, 'Use capital letters, digits and underscores, starting with a letter')

export const requiredText = (label: string, max: number) =>
  z.string().trim().min(1, `Enter ${label}`).max(max, `At most ${max} characters`)

export const optionalText = (max: number) => z.string().trim().max(max, `At most ${max} characters`)

/** A whole number typed into a text box; empty is allowed and means "not set". */
export const optionalWholeNumber = (min: number, max: number) =>
  z
    .string()
    .trim()
    .refine((value) => value === '' || /^\d+$/.test(value), 'Enter a whole number')
    .refine(
      (value) => value === '' || (Number(value) >= min && Number(value) <= max),
      `Must be between ${min} and ${max}`,
    )

/** An amount with at most two decimals; empty is allowed and means "not set". */
export const optionalAmount = z
  .string()
  .trim()
  .refine((value) => value === '' || /^\d{1,10}(\.\d{1,2})?$/.test(value), 'Enter an amount like 1500 or 1499.50')

export function toNumberOrNull(value: string): number | null {
  return value.trim() === '' ? null : Number(value)
}
