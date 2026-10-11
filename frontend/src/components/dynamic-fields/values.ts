import type { FieldValues, Path, UseFormReturn } from 'react-hook-form'
import { z } from 'zod'
import {
  DEFAULT_FILE_SIZE_MB,
  DEFAULT_TEXT_LENGTH,
  DEFAULT_TEXTAREA_LENGTH,
  fileReferenceSchema,
  type FieldDefinition,
  type FileReference,
} from '@/components/dynamic-fields/model'
import { ApiError } from '@/lib/api/errors'

/**
 * How answers are held while the form is being filled in: what the inputs produce.
 * Text, numbers, dates and single choices are strings; a yes/no is 'true', 'false' or '' (not
 * answered); a multiple choice is a list; a file is its reference once uploaded.
 */
export type FormAnswers = Record<string, string | string[] | FileReference | null>

/** What is sent to the server: field key to JSON value, without the unanswered fields. */
export type Answers = Record<string, string | number | boolean | string[] | FileReference>

/** The starting (unanswered) value of every field, or the given stored answers put back into form shape. */
export function initialAnswers(fields: FieldDefinition[], stored: Record<string, unknown> = {}): FormAnswers {
  const answers: FormAnswers = {}
  for (const field of fields) {
    const value = stored[field.key]
    if (field.type === 'MULTI_SELECT') {
      answers[field.key] = Array.isArray(value) ? value.map(String) : []
    } else if (field.type === 'FILE') {
      const reference = fileReferenceSchema.safeParse(value)
      answers[field.key] = reference.success ? reference.data : null
    } else {
      answers[field.key] = value === undefined || value === null ? '' : String(value)
    }
  }
  return answers
}

function numberRule(field: FieldDefinition, decimals: number) {
  // Money is never negative unless the administrator set a lower limit that says otherwise.
  const min = field.minValue ?? (field.type === 'CURRENCY' ? 0 : null)
  const max = field.maxValue ?? null
  return z.string().superRefine((text, context) => {
    const value = text.trim()
    if (value === '') {
      return
    }
    const issue = (message: string) => context.addIssue({ code: z.ZodIssueCode.custom, message })
    if (!/^-?\d+(\.\d+)?$/.test(value)) {
      issue('Enter a number')
    } else if ((value.split('.')[1]?.replace(/0+$/, '').length ?? 0) > decimals) {
      issue(`At most ${decimals} decimal places`)
    } else if (min !== null && Number(value) < min) {
      issue(`Must be at least ${min}`)
    } else if (max !== null && Number(value) > max) {
      issue(`Must be at most ${max}`)
    }
  })
}

/** The rule for one field's form value, apart from "required". Mirrors the server's CustomFieldValidator. */
function ruleFor(field: FieldDefinition): z.ZodTypeAny {
  const values = (field.options ?? []).map((option) => option.value)
  switch (field.type) {
    case 'NUMBER':
      return numberRule(field, 4)
    case 'CURRENCY':
      return numberRule(field, 2)
    case 'SELECT':
      return z.string().refine((value) => value === '' || values.includes(value), 'Choose one of the options')
    case 'MULTI_SELECT':
      return z.array(z.string()).refine((chosen) => chosen.every((value) => values.includes(value)), 'Choose from the options')
    case 'BOOLEAN':
      return z.enum(['', 'true', 'false'])
    case 'DATE':
      return z
        .string()
        .refine((value) => value === '' || (/^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(value))), 'Enter a date')
    case 'FILE':
      return fileReferenceSchema.nullable()
    default: {
      // TEXT, TEXTAREA, and any type this version does not know (shown as a text box; the server decides).
      const limit = field.maxLength ?? (field.type === 'TEXTAREA' ? DEFAULT_TEXTAREA_LENGTH : DEFAULT_TEXT_LENGTH)
      return z.string().refine((value) => value.trim().length <= limit, `At most ${limit} characters`)
    }
  }
}

function isAnswered(value: unknown) {
  return !(value === null || value === undefined || (typeof value === 'string' && value.trim() === '') || (Array.isArray(value) && value.length === 0))
}

/**
 * Builds the validation schema for a set of fields. It is generated from the definitions every
 * time they are loaded, so a field added by an administrator is validated without any code change.
 */
export function buildAnswersSchema(fields: FieldDefinition[]) {
  const shape: Record<string, z.ZodTypeAny> = {}
  for (const field of fields) {
    const rule = ruleFor(field)
    shape[field.key] = field.required ? rule.refine(isAnswered, 'This is required') : rule
  }
  return z.object(shape)
}

/** Turns what the inputs hold into what the server expects, leaving out the unanswered fields. */
export function toAnswers(fields: FieldDefinition[], form: FormAnswers): Answers {
  const answers: Answers = {}
  for (const field of fields) {
    const value = form[field.key]
    if (!isAnswered(value)) {
      continue
    }
    if (field.type === 'NUMBER' || field.type === 'CURRENCY') {
      answers[field.key] = Number(value)
    } else if (field.type === 'BOOLEAN') {
      answers[field.key] = value === 'true'
    } else if (typeof value === 'string') {
      answers[field.key] = value.trim()
    } else if (value) {
      answers[field.key] = value
    }
  }
  return answers
}

/** Checks a chosen file against the field's rules before it is uploaded. Returns the problem, or null. */
export function fileProblem(field: FieldDefinition, file: File): string | null {
  const allowed = field.allowedFileTypes ?? []
  const extension = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (allowed.length > 0 && !allowed.includes(extension)) {
    return `Must be a file of type ${allowed.join(', ')}`
  }
  const maxMb = field.maxFileSizeMb ?? DEFAULT_FILE_SIZE_MB
  if (file.size === 0) {
    return 'The file is empty'
  }
  if (file.size > maxMb * 1024 * 1024) {
    return `Must be at most ${maxMb} MB`
  }
  return null
}

/**
 * Shows the server's verdict on the answers in the form. The server reports each problem under the
 * field's key; `name` is where the answers live in the form (see DynamicFields). Returns a message
 * for anything that could not be placed next to a field, or null.
 */
export function applyAnswerErrors<T extends FieldValues>(
  form: UseFormReturn<T>,
  name: string,
  fields: FieldDefinition[],
  error: unknown,
): string | null {
  if (!(error instanceof ApiError)) {
    return 'Something went wrong. Please try again.'
  }
  const known = new Set(fields.map((field) => field.key))
  const unplaced: string[] = []
  for (const violation of error.fieldErrors) {
    if (known.has(violation.field)) {
      const message = violation.message.charAt(0).toUpperCase() + violation.message.slice(1)
      form.setError(`${name}.${violation.field}` as Path<T>, { type: 'server', message })
    } else {
      unplaced.push(`${violation.field} ${violation.message}`)
    }
  }
  if (error.fieldErrors.length === 0) {
    return error.message
  }
  return unplaced.length > 0 ? unplaced.join('; ') : null
}
