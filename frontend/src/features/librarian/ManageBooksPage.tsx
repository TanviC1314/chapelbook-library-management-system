import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { catalogueApi } from '../../api/catalogue'
import { describeError } from '../../api/client'
import type { Book } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { SelectField, TextField } from '../../components/ui/Field'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toast'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { BookFormModal } from './BookFormModal'
import { CategoriesModal } from './CategoriesModal'

const PAGE_SIZE = 15

/** Staff list of every title, to add, edit or remove books and to reach each title's copies. */
export default function ManageBooksPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const categoryId = params.get('categoryId') ? Number(params.get('categoryId')) : undefined
  const page = Math.max(Number(params.get('page') ?? 0) || 0, 0)
  const toast = useToast()
  const queryClient = useQueryClient()

  const [text, setText] = useState(q)
  const debounced = useDebouncedValue(text, 300)
  const [form, setForm] = useState<{ book?: Book } | null>(null)
  const [showCategories, setShowCategories] = useState(false)
  const [toDelete, setToDelete] = useState<Book | null>(null)

  function update(changes: Record<string, string | undefined>, resetPage = true) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === '') next.delete(key)
      else next.set(key, value)
    }
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  useEffect(() => {
    if (debounced.trim() !== q) update({ q: debounced.trim() || undefined })
    // update() is recreated every render; only a change in the debounced text should trigger this.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced])

  const categories = useQuery({ queryKey: ['categories'], queryFn: catalogueApi.listCategories, staleTime: 5 * 60_000 })
  const books = useQuery({
    queryKey: ['staff-books', { q, categoryId, page }],
    queryFn: () => catalogueApi.searchBooks({ q, categoryId, sort: 'TITLE', page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  const remove = useMutation({
    mutationFn: (id: number) => catalogueApi.deleteBook(id),
    onSuccess: () => {
      toast.success('Book deleted.')
      setToDelete(null)
      void queryClient.invalidateQueries({ queryKey: ['staff-books'] })
      void queryClient.invalidateQueries({ queryKey: ['books'] })
    },
    onError: (error) => {
      toast.error(describeError(error))
      setToDelete(null)
    },
  })

  return (
    <>
      <PageHeader
        title="Manage books"
        subtitle="Add and edit titles, and open a title to manage its copies."
        actions={
          <>
            <Button variant="secondary" onClick={() => setShowCategories(true)}>
              Categories
            </Button>
            <Button onClick={() => setForm({})} disabled={!categories.data}>
              Add a book
            </Button>
          </>
        }
      />

      <Card className="mb-6">
        <div className="grid gap-4 md:grid-cols-3">
          <div className="md:col-span-2">
            <TextField id="search" label="Search" type="search" placeholder="Title, author or ISBN" value={text} onChange={(e) => setText(e.target.value)} />
          </div>
          <SelectField id="category" label="Category" value={categoryId ?? ''} onChange={(e) => update({ categoryId: e.target.value || undefined })}>
            <option value="">All categories</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </SelectField>
        </div>
      </Card>

      <Card className="p-0 [&>div]:p-0">
        {books.isPending ? (
          <div className="space-y-3 p-5" aria-busy="true" aria-label="Loading books">
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
          </div>
        ) : books.isError ? (
          <ErrorState error={books.error} onRetry={() => books.refetch()} />
        ) : books.data.content.length === 0 ? (
          <EmptyState
            title="No books found"
            message={q || categoryId ? 'Try a different search.' : 'Add the first book to the library.'}
            action={!q && !categoryId && categories.data ? <Button onClick={() => setForm({})}>Add a book</Button> : undefined}
          />
        ) : (
          <>
            <div className={`overflow-x-auto ${books.isPlaceholderData ? 'opacity-60' : ''}`}>
              <table className="w-full min-w-[40rem] text-left text-sm">
                <thead className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-medium">Title</th>
                    <th scope="col" className="px-3 py-3 font-medium">Category</th>
                    <th scope="col" className="px-3 py-3 font-medium">Copies</th>
                    <th scope="col" className="px-5 py-3 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {books.data.content.map((book) => (
                    <tr key={book.id}>
                      <td className="px-5 py-3">
                        <Link to={`/librarian/books/${book.id}`} className="font-medium text-slate-900 hover:text-brand-700 hover:underline">
                          {book.title}
                        </Link>
                        <p className="text-xs text-slate-500">
                          {book.author}
                          {book.isbn ? ` · ${book.isbn}` : ''}
                        </p>
                      </td>
                      <td className="px-3 py-3 text-slate-600">{book.category.name}</td>
                      <td className="px-3 py-3 text-slate-600">
                        {book.availableCopies} of {book.totalCopies} available
                        {book.pendingReservations > 0 && <span className="block text-xs text-amber-700">{book.pendingReservations} waiting</span>}
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-2">
                          <Link to={`/librarian/books/${book.id}`}>
                            <Button variant="secondary" aria-label={`Copies of ${book.title}`}>
                              Copies
                            </Button>
                          </Link>
                          <Button variant="secondary" onClick={() => setForm({ book })} aria-label={`Edit ${book.title}`}>
                            Edit
                          </Button>
                          <Button variant="ghost" onClick={() => setToDelete(book)} aria-label={`Delete ${book.title}`}>
                            Delete
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={books.data.page}
              totalPages={books.data.totalPages}
              totalElements={books.data.totalElements}
              noun="book"
              onPageChange={(next) => update({ page: next === 0 ? undefined : String(next) }, false)}
            />
          </>
        )}
      </Card>

      {form && categories.data && <BookFormModal book={form.book} categories={categories.data} onClose={() => setForm(null)} />}
      {showCategories && <CategoriesModal onClose={() => setShowCategories(false)} />}
      {toDelete && (
        <ConfirmDialog
          title="Delete this book?"
          message={`“${toDelete.title}” will be removed from the catalogue. A book that has copies or reservations cannot be deleted: withdraw its copies instead.`}
          confirmLabel="Delete book"
          loading={remove.isPending}
          onConfirm={() => remove.mutate(toDelete.id)}
          onCancel={() => setToDelete(null)}
        />
      )}
    </>
  )
}
