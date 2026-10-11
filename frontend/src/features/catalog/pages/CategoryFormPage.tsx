import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { FormActions, FormCard, FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { ActivityTimeline } from '@/features/audit/components/ActivityTimeline'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import {
  catalogKeys,
  categoryQuery,
  createCategory,
  updateCategory,
  verticalsQuery,
  type Category,
} from '@/features/catalog/api'
import {
  codeField,
  optionalText,
  optionalWholeNumber,
  requiredText,
  toNumberOrNull,
} from '@/features/catalog/form-fields'
import { applyServerErrors, blankToNull } from '@/lib/forms'

const LIST = '/admin/categories'

const schema = z.object({
  vertical: z.string().min(1, 'Choose a vertical'),
  code: codeField(60),
  name: requiredText('a name', 150),
  description: optionalText(500),
  displayOrder: optionalWholeNumber(0, 9999),
})
type Values = z.infer<typeof schema>

/** Create a category (`/admin/categories/new`) or edit one (`/admin/categories/:id/edit`). */
export function CategoryFormPage() {
  const { id } = useParams()
  const existing = useQuery({ ...categoryQuery(id ?? ''), enabled: id !== undefined })

  return (
    <div className="grid gap-6">
      <PageHeader title={id ? 'Edit category' : 'New category'} back={{ to: LIST, label: 'Service categories' }} />
      {id && !existing.data ? (
        <RecordLoadState
          noun="category"
          isPending={existing.isPending}
          error={existing.error}
          onRetry={() => void existing.refetch()}
        />
      ) : (
        <>
          <CategoryForm key={existing.data?.version ?? 'new'} category={existing.data} />
          {existing.data ? <ActivityTimeline entityType="Category" entityId={existing.data.id} /> : null}
        </>
      )}
    </div>
  )
}

function CategoryForm({ category }: { category?: Category }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const verticals = useQuery(verticalsQuery)

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      vertical: category?.vertical.code ?? '',
      code: category?.code ?? '',
      name: category?.name ?? '',
      description: category?.description ?? '',
      displayOrder: category ? String(category.displayOrder) : '',
    },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    const input = {
      name: values.name,
      description: blankToNull(values.description),
      displayOrder: toNumberOrNull(values.displayOrder) ?? 0,
    }
    try {
      if (category) {
        await updateCategory(category.id, { ...input, version: category.version })
      } else {
        await createCategory({ ...input, vertical: values.vertical, code: values.code })
      }
      await queryClient.invalidateQueries({ queryKey: catalogKeys.categories })
      navigate(LIST)
    } catch (error) {
      setFormError(applyServerErrors(form, error))
    }
  }

  return (
    <FormCard>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" noValidate>
          <FormError message={formError} />

          <FormField
            control={form.control}
            name="vertical"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Vertical</FormLabel>
                <FormControl>
                  <Select {...field} disabled={!!category}>
                    <option value="">Choose a vertical…</option>
                    {(verticals.data ?? []).map((vertical) => (
                      <option key={vertical.code} value={vertical.code}>
                        {vertical.name}
                        {vertical.active ? '' : ' (inactive)'}
                      </option>
                    ))}
                  </Select>
                </FormControl>
                {category ? <FormDescription>A category stays in the vertical it was created in.</FormDescription> : null}
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Name</FormLabel>
                <FormControl>
                  <Input {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="code"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Code</FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    disabled={!!category}
                    className="font-mono"
                    onChange={(event) => field.onChange(event.target.value.toUpperCase().replace(/[\s-]+/g, '_'))}
                  />
                </FormControl>
                <FormDescription>
                  {category ? 'The code cannot be changed.' : 'A short identifier such as INSTALLATION. It cannot be changed later.'}
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="description"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Description</FormLabel>
                <FormControl>
                  <Textarea {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="displayOrder"
            render={({ field }) => (
              <FormItem className="sm:max-w-48">
                <FormLabel>Display order</FormLabel>
                <FormControl>
                  <Input inputMode="numeric" {...field} />
                </FormControl>
                <FormDescription>Lower numbers are listed first.</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormActions
            submitLabel={category ? 'Save changes' : 'Create category'}
            submitting={form.formState.isSubmitting}
            cancelTo={LIST}
          />
        </form>
      </Form>
    </FormCard>
  )
}
