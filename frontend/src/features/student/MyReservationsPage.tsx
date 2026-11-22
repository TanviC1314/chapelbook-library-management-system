import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { reservationsApi } from '../../api/circulation'
import { describeError } from '../../api/client'
import type { Reservation } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { formatDate } from '../../lib/format'

const PAGE_SIZE = 10

function detail(r: Reservation): string {
  switch (r.status) {
    case 'READY':
      return `A copy (${r.copyBarcode}) is held for you until ${formatDate(r.readyUntil!)}. Collect it from the desk.`
    case 'PENDING':
      return `You are number ${r.queuePosition} in the queue.`
    case 'FULFILLED':
      return 'You collected this book.'
    case 'EXPIRED':
      return 'The copy was not collected in time.'
    case 'CANCELLED':
      return 'You cancelled this reservation.'
  }
}

export default function MyReservationsPage() {
  const [params, setParams] = useSearchParams()
  const page = Math.max(Number(params.get('page') ?? 0) || 0, 0)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [toCancel, setToCancel] = useState<Reservation | null>(null)

  const reservations = useQuery({
    queryKey: ['my-reservations', { page }],
    queryFn: () => reservationsApi.mine({ page, size: PAGE_SIZE }),
  })

  const cancel = useMutation({
    mutationFn: (id: number) => reservationsApi.cancel(id),
    onSuccess: () => {
      toast.success('Reservation cancelled.')
      setToCancel(null)
      void queryClient.invalidateQueries({ queryKey: ['my-reservations'] })
      void queryClient.invalidateQueries({ queryKey: ['book'] })
      void queryClient.invalidateQueries({ queryKey: ['books'] })
    },
    onError: (error) => {
      toast.error(describeError(error))
      setToCancel(null)
      void queryClient.invalidateQueries({ queryKey: ['my-reservations'] })
    },
  })

  return (
    <>
      <PageHeader title="My reservations" subtitle="Books you have reserved or are waiting for." />
      <Card className="p-0 [&>div]:p-0">
        {reservations.isPending ? (
          <div className="space-y-3 p-5" aria-busy="true" aria-label="Loading reservations">
            <Skeleton className="h-16" />
            <Skeleton className="h-16" />
          </div>
        ) : reservations.isError ? (
          <ErrorState error={reservations.error} onRetry={() => reservations.refetch()} />
        ) : reservations.data.content.length === 0 ? (
          <EmptyState
            title="You have no reservations"
            message="Reserve a book from the catalogue and it will show up here."
            action={
              <Link to="/catalogue" className="text-sm font-medium text-brand-700 hover:underline">
                Browse the catalogue
              </Link>
            }
          />
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {reservations.data.content.map((r) => (
                <li key={r.id} className="flex flex-wrap items-start justify-between gap-3 px-5 py-4">
                  <div className="min-w-0">
                    <Link to={`/catalogue/${r.bookId}`} className="font-medium text-slate-900 hover:text-brand-700 hover:underline">
                      {r.bookTitle}
                    </Link>
                    <p className="mt-1 text-sm text-slate-600">{detail(r)}</p>
                    <p className="mt-1 text-xs text-slate-400">Reserved on {formatDate(r.reservedAt)}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <StatusBadge status={r.status} />
                    {(r.status === 'PENDING' || r.status === 'READY') && (
                      <Button variant="secondary" onClick={() => setToCancel(r)} aria-label={`Cancel reservation for ${r.bookTitle}`}>
                        Cancel
                      </Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
            <Pagination
              page={reservations.data.page}
              totalPages={reservations.data.totalPages}
              totalElements={reservations.data.totalElements}
              noun="reservation"
              onPageChange={(next) => setParams(next === 0 ? {} : { page: String(next) }, { replace: true })}
            />
          </>
        )}
      </Card>

      {toCancel && (
        <ConfirmDialog
          title="Cancel this reservation?"
          message={
            toCancel.status === 'READY'
              ? `The copy being held for you will be offered to the next student. You can reserve “${toCancel.bookTitle}” again later.`
              : `You will lose your place in the queue for “${toCancel.bookTitle}”.`
          }
          confirmLabel="Cancel reservation"
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(toCancel.id)}
          onCancel={() => setToCancel(null)}
        />
      )}
    </>
  )
}
