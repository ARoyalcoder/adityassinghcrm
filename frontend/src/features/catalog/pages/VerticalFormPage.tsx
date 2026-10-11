import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { FormActions, FormCard, FormError, RecordLoadState } from '@/components/forms/FormParts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { catalogKeys, updateVertical, verticalsQuery, type Vertical } from '@/features/catalog/api'
import { optionalText, requiredText } from '@/features/catalog/form-fields'
import { applyServerErrors, blankToNull } from '@/lib/forms'
import { NotFoundPage } from '@/pages/NotFoundPage'

const LIST = '/admin/verticals'

const schema = z.object({
  name: requiredText('a name', 100),
  description: optionalText(500),
  displayOrder: z
    .string()
    .trim()
    .regex(/^\d+$/, 'Enter a whole number')
    .refine((value) => Number(value) >= 1 && Number(value) <= 999, 'Must be between 1 and 999'),
})
type Values = z.infer<typeof schema>

/** Edit one of the six verticals (`/admin/verticals/:code/edit`). There is no "new vertical" page. */
export function VerticalFormPage() {
  const { code } = useParams()
  const verticals = useQuery(verticalsQuery)
  const vertical = verticals.data?.find((item) => item.code === code)

  if (verticals.data && !vertical) {
    return <NotFoundPage />
  }

  return (
    <div className="grid gap-6">
      <PageHeader title="Edit vertical" back={{ to: LIST, label: 'Verticals' }} />
      {vertical ? (
        <VerticalForm key={vertical.version} vertical={vertical} />
      ) : (
        <RecordLoadState
          noun="vertical"
          isPending={verticals.isPending}
          error={verticals.error}
          onRetry={() => void verticals.refetch()}
        />
      )}
    </div>
  )
}

function VerticalForm({ vertical }: { vertical: Vertical }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: vertical.name,
      description: vertical.description ?? '',
      displayOrder: String(vertical.displayOrder),
    },
  })

  async function onSubmit(values: Values) {
    setFormError(null)
    try {
      await updateVertical(vertical.code, {
        name: values.name,
        description: blankToNull(values.description),
        displayOrder: Number(values.displayOrder),
        version: vertical.version,
      })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: catalogKeys.verticals }),
        queryClient.invalidateQueries({ queryKey: ['service-verticals'] }),
      ])
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

          <div className="grid gap-1 text-sm">
            <span className="font-medium">Code</span>
            <span className="font-mono text-muted-foreground">{vertical.code}</span>
            <span className="text-muted-foreground">The code identifies the vertical and cannot be changed.</span>
          </div>

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
                <FormDescription>Each vertical needs its own number; lower is listed first.</FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormActions submitLabel="Save changes" submitting={form.formState.isSubmitting} cancelTo={LIST} />
        </form>
      </Form>
    </FormCard>
  )
}
