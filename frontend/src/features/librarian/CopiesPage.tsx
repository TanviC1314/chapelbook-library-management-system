import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useParams } from 'react-router-dom'
import { z } from 'zod'
import { catalogueApi } from '../../api/catalogue'
import { describeError } from '../../api/client'
import type { Copy, CopyStatus } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { AvailabilityPill } from '../../components/ui/AvailabilityPill'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { TextField } from '../../components/ui/Field'
import { Modal } from '../../components/ui/Modal'
import { PageHeader } from '../../components/ui/PageHeader'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'

const copySchema = z.object({
  barcode: z.string().trim().min(1, 'Barcode is required').max(64, 'Barcode must be at most 64 characters'),
  shelfLocation: z.string().trim().max(100, 'Shelf location must be at most 100 characters'),
})
type CopyForm = z.infer<typeof copySchema>

/** The only status changes staff may make by hand; lending, returning and reserving set the others. */
type StaffStatus = Extract<CopyStatus, 'AVAILABLE' | 'LOST' | 'WITHDRAWN'>
const CHANGEABLE: CopyStatus[] = ['AVAILABLE', 'LOST', 'WITHDRAWN']

const explanations: Record<StaffStatus, { title: string; message: string; confirm: string }> = {
  WITHDRAWN: {
    title: 'Withdraw this copy?',
    message: 'It will be taken out of circulation (damaged or retired). Its borrowing history is kept, and you can restore it later.',
    confirm: 'Withdraw copy',
  },
  LOST: {
    title: 'Mark this copy as lost?',
    message: 'It will no longer count as available. If it turns up you can restore it to the shelf.',
    confirm: 'Mark as lost',
  },
  AVAILABLE: {
    title: 'Put this copy back on the shelf?',
    message: 'It becomes available again. If a student is waiting for this title, it is held for them straight away.',
    confirm: 'Restore to shelf',
  },
}

function lockedReason(status: CopyStatus): string | null {
  if (status === 'ON_LOAN') return 'On loan: take it back at the issue and return desk.'
  if (status === 'HELD') return 'Held for a student who reserved it.'
  return null
}

export default function CopiesPage() {
  const { id } = useParams()
  const bookId = Number(id)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<Copy | null>(null)
  const [change, setChange] = useState<{ copy: Copy; status: StaffStatus } | null>(null)

  const book = useQuery({ queryKey: ['book', bookId], queryFn: () => catalogueApi.getBook(bookId), enabled: Number.isFinite(bookId) })
  const copies = useQuery({ queryKey: ['copies', bookId], queryFn: () => catalogueApi.listCopies(bookId), enabled: Number.isFinite(bookId) })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['copies', bookId] })
    void queryClient.invalidateQueries({ queryKey: ['book', bookId] })
    void queryClient.invalidateQueries({ queryKey: ['staff-books'] })
    void queryClient.invalidateQueries({ queryKey: ['books'] })
  }

  const setStatus = useMutation({
    mutationFn: ({ copy, status }: { copy: Copy; status: StaffStatus }) => catalogueApi.updateCopy(copy.id, { status }),
    onSuccess: (copy) => {
      toast.success(
        copy.status === 'HELD'
          ? 'Restored. A student was waiting, so this copy is now held for them.'
          : `Copy ${copy.barcode} is now ${copy.status.toLowerCase()}.`,
      )
      setChange(null)
      refresh()
    },
    onError: (error) => {
      toast.error(describeError(error))
      setChange(null)
      refresh()
    },
  })

  if (book.isPending || copies.isPending) return <Skeleton className="h-64" />
  if (book.isError) {
    return (
      <Card>
        <ErrorState error={book.error} onRetry={() => book.refetch()} />
      </Card>
    )
  }

  return (
    <>
      <Link to="/librarian/books" className="mb-4 inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Back to all books
      </Link>
      <PageHeader
        title={book.data.title}
        subtitle={`${book.data.author} · ${book.data.category.name}`}
        actions={<Button onClick={() => setAdding(true)}>Add a copy</Button>}
      />
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <AvailabilityPill book={book.data} />
        {book.data.pendingReservations > 0 && <span className="text-sm text-amber-700">{book.data.pendingReservations} waiting in the queue</span>}
      </div>

      <Card title="Copies" className="p-0 [&>div]:p-0">
        {copies.isError ? (
          <ErrorState error={copies.error} onRetry={() => copies.refetch()} />
        ) : copies.data.length === 0 ? (
          <EmptyState title="This title has no copies yet" message="Add a copy so students can borrow or reserve it." action={<Button onClick={() => setAdding(true)}>Add a copy</Button>} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[36rem] text-left text-sm">
              <thead className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="px-5 py-3 font-medium">Barcode</th>
                  <th scope="col" className="px-3 py-3 font-medium">Status</th>
                  <th scope="col" className="px-3 py-3 font-medium">Shelf</th>
                  <th scope="col" className="px-5 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {copies.data.map((copy) => {
                  const locked = lockedReason(copy.status)
                  return (
                    <tr key={copy.id}>
                      <td className="px-5 py-3 font-mono text-slate-900">{copy.barcode}</td>
                      <td className="px-3 py-3">
                        <StatusBadge status={copy.status} />
                        {locked && <p className="mt-1 text-xs text-slate-500">{locked}</p>}
                      </td>
                      <td className="px-3 py-3 text-slate-600">{copy.shelfLocation ?? '—'}</td>
                      <td className="px-5 py-3">
                        <div className="flex flex-wrap justify-end gap-2">
                          <Button variant="secondary" onClick={() => setEditing(copy)} aria-label={`Edit shelf of ${copy.barcode}`}>
                            Edit shelf
                          </Button>
                          {copy.status === 'AVAILABLE' && (
                            <>
                              <Button variant="ghost" onClick={() => setChange({ copy, status: 'WITHDRAWN' })} aria-label={`Withdraw ${copy.barcode}`}>
                                Withdraw
                              </Button>
                              <Button variant="ghost" onClick={() => setChange({ copy, status: 'LOST' })} aria-label={`Mark ${copy.barcode} as lost`}>
                                Mark lost
                              </Button>
                            </>
                          )}
                          {(copy.status === 'LOST' || copy.status === 'WITHDRAWN') && (
                            <Button variant="secondary" onClick={() => setChange({ copy, status: 'AVAILABLE' })} aria-label={`Restore ${copy.barcode} to the shelf`}>
                              Restore to shelf
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {adding && <CopyFormModal bookId={bookId} onClose={() => setAdding(false)} onSaved={refresh} />}
      {editing && <CopyFormModal bookId={bookId} copy={editing} onClose={() => setEditing(null)} onSaved={refresh} />}
      {change && CHANGEABLE.includes(change.copy.status) && (
        <ConfirmDialog
          title={explanations[change.status].title}
          message={explanations[change.status].message}
          confirmLabel={explanations[change.status].confirm}
          loading={setStatus.isPending}
          onConfirm={() => setStatus.mutate(change)}
          onCancel={() => setChange(null)}
        />
      )}
    </>
  )
}

function CopyFormModal({ bookId, copy, onClose, onSaved }: { bookId: number; copy?: Copy; onClose: () => void; onSaved: () => void }) {
  const toast = useToast()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CopyForm>({ resolver: zodResolver(copySchema), defaultValues: { barcode: copy?.barcode ?? '', shelfLocation: copy?.shelfLocation ?? '' } })

  const save = useMutation({
    mutationFn: (form: CopyForm) =>
      copy
        ? catalogueApi.updateCopy(copy.id, { shelfLocation: form.shelfLocation || null })
        : catalogueApi.addCopy(bookId, { barcode: form.barcode, shelfLocation: form.shelfLocation || null }),
    onSuccess: (saved) => {
      toast.success(
        copy
          ? 'Shelf location saved.'
          : saved.status === 'HELD'
            ? `Copy ${saved.barcode} added and held for the student waiting longest.`
            : `Copy ${saved.barcode} added.`,
      )
      onSaved()
      onClose()
    },
    onError: (error) => setFormError(describeError(error)),
  })

  return (
    <Modal title={copy ? `Edit ${copy.barcode}` : 'Add a copy'} onClose={onClose}>
      <form onSubmit={handleSubmit((form) => save.mutate(form))} noValidate className="space-y-4">
        {formError && <Alert>{formError}</Alert>}
        <TextField
          id="barcode"
          label="Barcode"
          disabled={!!copy}
          hint={copy ? 'A copy keeps its barcode.' : 'Scan or type the label on the book.'}
          error={errors.barcode?.message}
          {...register('barcode')}
        />
        <TextField id="shelfLocation" label="Shelf location (optional)" error={errors.shelfLocation?.message} {...register('shelfLocation')} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {copy ? 'Save' : 'Add copy'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
