import { z } from 'zod'

/**
 * A custom field as configured by an administrator (the backend's LeadCustomFields.FieldDefinition).
 * Everything the screen needs to ask for and check an answer is in here: no component names a field.
 */
export const fieldDefinitionSchema = z.object({
  id: z.string(),
  /** The field's name in the answers sent to the server. */
  key: z.string(),
  label: z.string(),
  /** One of FIELD_TYPES. A type this version does not know is still shown, as a text box. */
  type: z.string(),
  helpText: z.string().nullish(),
  required: z.boolean(),
  options: z.array(z.object({ value: z.string(), label: z.string() })).nullish(),
  minValue: z.number().nullish(),
  maxValue: z.number().nullish(),
  maxLength: z.number().nullish(),
  unit: z.string().nullish(),
  allowedFileTypes: z.array(z.string()).nullish(),
  maxFileSizeMb: z.number().nullish(),
  displayOrder: z.number(),
  active: z.boolean(),
})
export type FieldDefinition = z.infer<typeof fieldDefinitionSchema>

/** The answer to a FILE field: where the uploaded file was stored. */
export const fileReferenceSchema = z.object({
  storageKey: z.string(),
  fileName: z.string(),
  contentType: z.string().nullish(),
  sizeBytes: z.number(),
})
export type FileReference = z.infer<typeof fileReferenceSchema>

/** Must match the backend enum catalog.api.CustomFieldType. */
export const FIELD_TYPES = [
  { value: 'TEXT', label: 'Text' },
  { value: 'NUMBER', label: 'Number' },
  { value: 'CURRENCY', label: 'Currency' },
  { value: 'SELECT', label: 'Select' },
  { value: 'MULTI_SELECT', label: 'Multi-select' },
  { value: 'BOOLEAN', label: 'Yes / No' },
  { value: 'DATE', label: 'Date' },
  { value: 'TEXTAREA', label: 'Long text' },
  { value: 'FILE', label: 'File' },
] as const

export function fieldTypeLabel(type: string) {
  return FIELD_TYPES.find((option) => option.value === type)?.label ?? type
}

// Defaults the server applies when a field sets no limit of its own.
export const DEFAULT_TEXT_LENGTH = 255
export const DEFAULT_TEXTAREA_LENGTH = 4000
export const DEFAULT_FILE_SIZE_MB = 10

/** Uploads a file for a FILE field and resolves to its reference. Supplied by the form that hosts the fields. */
export type UploadFile = (field: FieldDefinition, file: File) => Promise<FileReference>
