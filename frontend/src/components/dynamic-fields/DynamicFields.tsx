import { useState, type ComponentType } from 'react'
import { useFormContext, type ControllerRenderProps, type FieldValues } from 'react-hook-form'
import {
  DEFAULT_FILE_SIZE_MB,
  type FieldDefinition,
  type FileReference,
  type UploadFile,
} from '@/components/dynamic-fields/model'
import { fileProblem } from '@/components/dynamic-fields/values'
import { Button } from '@/components/ui/button'
import { FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/errors'
import { cn } from '@/lib/utils'

/** The id and aria attributes that tie a control to its label, help text and error message. */
type ControlAttributes = { id?: string; 'aria-describedby'?: string; 'aria-invalid'?: boolean }

type InputProps = {
  /** Every input spreads these onto its control. */
  aria: ControlAttributes
  field: FieldDefinition
  /** The form's handle on this answer: value, onChange, onBlur, name, ref. */
  input: ControllerRenderProps<FieldValues, string>
  uploadFile?: UploadFile
}

function TextInput({ input, aria }: InputProps) {
  return <Input {...aria} {...input} value={(input.value as string) ?? ''} />
}

function LongTextInput({ input, aria }: InputProps) {
  return <Textarea {...aria} {...input} value={(input.value as string) ?? ''} />
}

function NumberInput({ field, input, aria }: InputProps) {
  const affix = field.type === 'CURRENCY' ? '₹' : field.unit
  return (
    <div className="flex items-center gap-2">
      {field.type === 'CURRENCY' ? <span className="text-sm text-muted-foreground">{affix}</span> : null}
      <Input {...aria} {...input} value={(input.value as string) ?? ''} inputMode="decimal" />
      {field.type !== 'CURRENCY' && affix ? (
        <span className="text-sm whitespace-nowrap text-muted-foreground">{affix}</span>
      ) : null}
    </div>
  )
}

function DateInput({ input, aria }: InputProps) {
  return <Input type="date" {...aria} {...input} value={(input.value as string) ?? ''} />
}

function SelectInput({ field, input, aria }: InputProps) {
  return (
    <Select {...aria} {...input} value={(input.value as string) ?? ''}>
      <option value="">{field.required ? 'Choose…' : 'Not specified'}</option>
      {(field.options ?? []).map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </Select>
  )
}

/** A set of radio buttons or checkboxes, named after the field for screen readers. */
function ChoiceGroup({
  label,
  input,
  choices,
  multiple,
  ...aria
}: ControlAttributes & {
  label: string
  input: ControllerRenderProps<FieldValues, string>
  choices: { value: string; label: string }[]
  multiple: boolean
}) {
  const { ref, name, value, onBlur, onChange } = input
  const chosen: string[] = multiple ? ((value as string[]) ?? []) : [(value as string) ?? '']

  function toggle(choice: string, checked: boolean) {
    if (!multiple) {
      onChange(choice)
    } else if (checked) {
      onChange([...chosen, choice])
    } else {
      onChange(chosen.filter((other) => other !== choice))
    }
  }

  return (
    <div
      {...aria}
      role={multiple ? 'group' : 'radiogroup'}
      aria-label={label}
      ref={ref}
      tabIndex={-1}
      className="flex flex-wrap gap-x-5 gap-y-2"
    >
      {choices.map((choice) => (
        <label key={choice.value} className="flex items-center gap-2 text-sm">
          <input
            type={multiple ? 'checkbox' : 'radio'}
            className="size-4 accent-primary"
            name={name}
            value={choice.value}
            checked={chosen.includes(choice.value)}
            onBlur={onBlur}
            onChange={(event) => toggle(choice.value, event.target.checked)}
          />
          {choice.label}
        </label>
      ))}
    </div>
  )
}

function MultiSelectInput({ field, input, aria }: InputProps) {
  return <ChoiceGroup {...aria} label={field.label} input={input} choices={field.options ?? []} multiple />
}

function YesNoInput({ field, input, aria }: InputProps) {
  const choices = [
    { value: 'true', label: 'Yes' },
    { value: 'false', label: 'No' },
    // An optional question can be put back to "not answered".
    ...(field.required ? [] : [{ value: '', label: 'Not specified' }]),
  ]
  return <ChoiceGroup {...aria} label={field.label} input={input} choices={choices} multiple={false} />
}

function FileInput({ field, input, uploadFile, aria }: InputProps) {
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  const { ref, name, value, onBlur, onChange } = input
  const current = value as FileReference | null
  const accept = (field.allowedFileTypes ?? []).map((type) => `.${type}`).join(',')

  async function choose(file: File | undefined) {
    if (!file) {
      return
    }
    const refused = fileProblem(field, file)
    if (refused || !uploadFile) {
      setProblem(refused ?? 'Files cannot be uploaded here.')
      return
    }
    setProblem(null)
    setBusy(true)
    try {
      onChange(await uploadFile(field, file))
    } catch (error) {
      const fromServer = error instanceof ApiError ? (error.fieldErrors[0]?.message ?? error.message) : null
      setProblem(fromServer ?? 'The file could not be uploaded. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid gap-2">
      {current ? (
        <p className="flex flex-wrap items-center gap-3 text-sm">
          <span>
            {current.fileName} <span className="text-muted-foreground">({formatSize(current.sizeBytes)})</span>
          </span>
          <Button type="button" variant="ghost" size="sm" onClick={() => onChange(null)}>
            Remove file
          </Button>
        </p>
      ) : null}
      <Input
        {...aria}
        type="file"
        ref={ref}
        name={name}
        accept={accept || undefined}
        disabled={busy}
        onBlur={onBlur}
        onChange={(event) => {
          void choose(event.target.files?.[0])
          event.target.value = ''
        }}
      />
      <p className="text-xs text-muted-foreground">
        {accept ? `${(field.allowedFileTypes ?? []).join(', ')} · ` : ''}up to {field.maxFileSizeMb ?? DEFAULT_FILE_SIZE_MB} MB
      </p>
      {busy ? <p className="text-sm text-muted-foreground">Uploading…</p> : null}
      {problem ? (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      ) : null}
    </div>
  )
}

function formatSize(bytes: number) {
  return bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

/**
 * Which input draws which type of field. This is the only place a field type meets a component;
 * a new type is supported by adding a line here. A type that is not listed is drawn as a text box.
 */
const INPUTS: Record<string, ComponentType<InputProps>> = {
  TEXT: TextInput,
  TEXTAREA: LongTextInput,
  NUMBER: NumberInput,
  CURRENCY: NumberInput,
  SELECT: SelectInput,
  MULTI_SELECT: MultiSelectInput,
  BOOLEAN: YesNoInput,
  DATE: DateInput,
  FILE: FileInput,
}

/** Receives the attributes FormControl adds and hands them to the input as one prop. */
function Control({
  component: Component,
  field,
  input,
  uploadFile,
  ...aria
}: Omit<InputProps, 'aria'> & ControlAttributes & { component: ComponentType<InputProps> }) {
  return <Component field={field} input={input} uploadFile={uploadFile} aria={aria} />
}

type DynamicFieldsProps = {
  /** The fields to ask for, in order: what the server returned for the chosen service. */
  definitions: FieldDefinition[]
  /** Where the answers live in the surrounding form's values, e.g. "customFields". */
  name: string
  /** Needed only if a FILE field may be present. */
  uploadFile?: UploadFile
  className?: string
}

/**
 * Renders a form section from field definitions. It must be inside a react-hook-form `<Form>`;
 * the answers are kept under `name` in that form's values (start them with `initialAnswers`,
 * validate them with `buildAnswersSchema`, send them with `toAnswers`).
 *
 * Nothing here knows any particular field: give it other definitions and it draws another form.
 */
export function DynamicFields({ definitions, name, uploadFile, className }: DynamicFieldsProps) {
  const form = useFormContext()
  return (
    <div className={cn('grid gap-4 sm:grid-cols-2', className)}>
      {definitions.map((field) => {
        const Component = INPUTS[field.type] ?? TextInput
        const wide = field.type === 'TEXTAREA' || field.type === 'MULTI_SELECT' || field.type === 'FILE'
        return (
          <FormField
            key={field.id}
            control={form.control}
            name={`${name}.${field.key}`}
            render={({ field: input }) => (
              <FormItem className={wide ? 'sm:col-span-2' : undefined} data-field-type={field.type}>
                <FormLabel>
                  {field.label}
                  {field.required ? (
                    <span className="text-destructive" aria-hidden="true">
                      {' '}
                      *
                    </span>
                  ) : null}
                </FormLabel>
                <FormControl>
                  <Control component={Component} field={field} input={input} uploadFile={uploadFile} />
                </FormControl>
                {field.helpText ? <FormDescription>{field.helpText}</FormDescription> : null}
                <FormMessage />
              </FormItem>
            )}
          />
        )
      })}
    </div>
  )
}
