import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * Search, filters and page number of a list screen, kept in the URL so a filtered list can be
 * bookmarked, shared and survives a refresh. Changing any filter returns to the first page.
 */
export function useListParams() {
  const [params, setParams] = useSearchParams()

  const get = useCallback((key: string) => params.get(key) ?? '', [params])

  /** Sets several parameters in one navigation; an empty value removes the parameter. */
  const setMany = useCallback(
    (changes: Record<string, string>) => {
      // Start from the address bar, not from this render's copy: two filters changed in quick
      // succession must both survive, and the second call runs before React has re-rendered.
      const next = new URLSearchParams(window.location.search)
      for (const [key, value] of Object.entries(changes)) {
        if (value) {
          next.set(key, value)
        } else {
          next.delete(key)
        }
      }
      if (!('page' in changes)) {
        next.delete('page')
      }
      setParams(next, { replace: true })
    },
    [setParams],
  )

  const set = useCallback((key: string, value: string) => setMany({ [key]: value }), [setMany])

  const pageParam = Number.parseInt(params.get('page') ?? '0', 10)
  const page = Number.isFinite(pageParam) && pageParam > 0 ? pageParam : 0
  const setPage = useCallback((next: number) => setMany({ page: next > 0 ? String(next) : '' }), [setMany])

  return { get, set, setMany, page, setPage }
}
