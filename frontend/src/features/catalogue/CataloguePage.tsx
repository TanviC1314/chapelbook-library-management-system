import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { catalogueApi, type BookSort } from '../../api/catalogue'
import { AvailabilityPill } from '../../components/ui/AvailabilityPill'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { SelectField, TextField } from '../../components/ui/Field'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { useDebouncedValue } from '../../lib/useDebouncedValue'

const PAGE_SIZE = 12
const SORTS: { value: BookSort; label: string }[] = [
  { value: 'TITLE', label: 'Title' },
  { value: 'AUTHOR', label: 'Author' },
  { value: 'YEAR', label: 'Newest publication' },
  { value: 'NEWEST', label: 'Recently added' },
]

function parseSort(value: string | null): BookSort {
  return SORTS.some((s) => s.value === value) ? (value as BookSort) : 'TITLE'
}

/** The catalogue. Every filter lives in the address, so a search can be bookmarked, shared and survives the back button. */
export default function CataloguePage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const categoryId = params.get('categoryId') ? Number(params.get('categoryId')) : undefined
  const availableOnly = params.get('availableOnly') === 'true'
  const sort = parseSort(params.get('sort'))
  const page = Math.max(Number(params.get('page') ?? 0) || 0, 0)

  const [text, setText] = useState(q)
  const debouncedText = useDebouncedValue(text, 300)

  function update(changes: Record<string, string | undefined>, resetPage = true) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === '') next.delete(key)
      else next.set(key, value)
    }
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  const filtered = text.trim() !== '' || q !== '' || categoryId !== undefined || availableOnly || sort !== 'TITLE'

  function clearFilters() {
    setText('')
    setParams({}, { replace: true })
  }

  // Write the typed text into the address once the person pauses typing.
  useEffect(() => {
    if (debouncedText.trim() !== q) update({ q: debouncedText.trim() || undefined })
    // update() is recreated every render; only a change in the debounced text should trigger this.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedText])

  const categories = useQuery({ queryKey: ['categories'], queryFn: catalogueApi.listCategories, staleTime: 5 * 60_000 })
  const books = useQuery({
    queryKey: ['books', { q, categoryId, availableOnly, sort, page }],
    queryFn: () => catalogueApi.searchBooks({ q, categoryId, availableOnly, sort, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader title="Catalogue" subtitle="Search the library by title, author or ISBN." />

      <Card className="mb-6">
        <div className="grid gap-4 md:grid-cols-4">
          <div className="md:col-span-2">
            <TextField
              id="search"
              label="Search"
              type="search"
              placeholder="Title, author or ISBN"
              value={text}
              onChange={(event) => setText(event.target.value)}
            />
          </div>
          <SelectField
            id="category"
            label="Category"
            value={categoryId ?? ''}
            onChange={(event) => update({ categoryId: event.target.value || undefined })}
          >
            <option value="">All categories</option>
            {categories.data?.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </SelectField>
          <SelectField id="sort" label="Sort by" value={sort} onChange={(event) => update({ sort: event.target.value })}>
            {SORTS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={availableOnly}
              onChange={(event) => update({ availableOnly: event.target.checked ? 'true' : undefined })}
              className="h-4 w-4 rounded border-slate-300 text-brand-700 focus:ring-brand-600"
            />
            Only show books available now
          </label>
          {filtered && (
            <Button variant="secondary" onClick={clearFilters}>
              Clear filters
            </Button>
          )}
        </div>
      </Card>

      {books.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Loading books">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-36" />
          ))}
        </div>
      ) : books.isError ? (
        <Card>
          <ErrorState error={books.error} onRetry={() => books.refetch()} />
        </Card>
      ) : books.data.content.length === 0 ? (
        <Card>
          <EmptyState
            title="No books match your search"
            message="Try different words, another category, or untick “only show books available now”."
          />
        </Card>
      ) : (
        <>
          <ul className={`grid gap-4 sm:grid-cols-2 lg:grid-cols-3 ${books.isPlaceholderData ? 'opacity-60' : ''}`}>
            {books.data.content.map((book) => (
              <li key={book.id}>
                <Link
                  to={`/catalogue/${book.id}`}
                  className="flex h-full flex-col rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 transition hover:ring-brand-500"
                >
                  <span className="text-xs font-medium uppercase tracking-wide text-brand-700">{book.category.name}</span>
                  <span className="mt-1 text-base font-semibold text-slate-900">{book.title}</span>
                  <span className="text-sm text-slate-600">
                    {book.author}
                    {book.publicationYear ? ` · ${book.publicationYear}` : ''}
                  </span>
                  <span className="mt-auto pt-4">
                    <AvailabilityPill book={book} />
                  </span>
                </Link>
              </li>
            ))}
          </ul>
          <div className="mt-4 rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            <Pagination
              page={books.data.page}
              totalPages={books.data.totalPages}
              totalElements={books.data.totalElements}
              noun="book"
              onPageChange={(next) => update({ page: next === 0 ? undefined : String(next) }, false)}
            />
          </div>
        </>
      )}
    </>
  )
}
