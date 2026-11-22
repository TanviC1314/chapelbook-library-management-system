import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { reservationsApi } from '../../api/circulation'
import { describeError } from '../../api/client'
import type { Reservation, ReservationStatus } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { SelectField } from '../../components/ui/Field'
import { PageHeader } from '../../components/ui/PageHeader'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { useToast } from '../../components/ui/Toast'
import { formatDate } from '../../lib/format'

const PAGE_SIZE = 15
const STATUSES: ReservationStatus[] = ['READY', 'PENDING', 'FULFILLED', 'CANCELLED', 'EXPIRED']

function parseStatus(value: string | null): ReservationStatus | undefined {
  return STATUSES.find((s) => s === value)
}

function detail(r: Reservation): string {
  if (r.status === 'READY') return `Copy ${r.copyBarcode} held until ${formatDate(r.readyUntil!)}`
  if (r.status === 'PENDING') return `Number ${r.queuePosition} in the queue`
  return `Reserved ${formatDate(r.reservedAt)}`
}

/** Everyone's reservations, so staff can see what is waiting at the desk and cancel one if needed. */
export default function StaffReservationsPage() {
  const [params, setParams] = useSearchParams()
  const status = parseStatus(params.get('status'))
  const page = Math.max(Number(params.get('page') ?? 0) || 0, 0)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [toCancel, setToCancel] = useState<Reservation | null>(null)

  const reservations = useQuery({
    queryKey: ['staff-reservations', { status, page }],
    queryFn: () => reservationsApi.list({ status, page, size: PAGE_SIZE }),
  })

  const cancel = useMutation({
    mutationFn: (id: number) => reservationsApi.cancel(id),
    onSuccess: () => {
      toast.success('Reservation cancelled. The copy, if one was held, goes to the next student in line.')
      setToCancel(null)
      void queryClient.invalidateQueries({ queryKey: ['staff-reservations'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      void queryClient.invalidateQueries({ queryKey: ['books'] })
    },
    onError: (error) => {
      toast.error(describeError(error))
      setToCancel(null)
      void queryClient.invalidateQueries({ queryKey: ['staff-reservations'] })
    },
  })

  return (
    <>
      <PageHeader title="Reservations" subtitle="Books students have reserved or are waiting for." />

      <div className="mb-4 max-w-xs">
        <SelectField
          id="status"
          label="Show"
          value={status ?? ''}
          onChange={(e) => setParams(e.target.value ? { status: e.target.value } : {}, { replace: true })}
        >
          <option value="">All reservations</option>
          <option value="READY">Ready to collect</option>
          <option value="PENDING">In the queue</option>
          <option value="FULFILLED">Collected</option>
          <option value="CANCELLED">Cancelled</option>
          <option value="EXPIRED">Expired</option>
        </SelectField>
      </div>

      <Card className="p-0 [&>div]:p-0">
        {reservations.isPending ? (
          <div className="space-y-3 p-5" aria-busy="true" aria-label="Loading reservations">
            <Skeleton className="h-14" />
            <Skeleton className="h-14" />
          </div>
        ) : reservations.isError ? (
          <ErrorState error={reservations.error} onRetry={() => reservations.refetch()} />
        ) : reservations.data.content.length === 0 ? (
          <EmptyState title="No reservations" message={status ? 'Nothing has this status.' : 'Students have not reserved anything yet.'} />
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {reservations.data.content.map((r) => (
                <li key={r.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">{r.bookTitle}</p>
                    <p className="text-sm text-slate-600">
                      {r.userName} · {detail(r)}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <StatusBadge status={r.status} />
                    {(r.status === 'READY' || r.status === 'PENDING') && (
                      <Button variant="secondary" onClick={() => setToCancel(r)} aria-label={`Cancel ${r.userName}'s reservation for ${r.bookTitle}`}>
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
              onPageChange={(next) => setParams({ ...(status ? { status } : {}), ...(next ? { page: String(next) } : {}) }, { replace: true })}
            />
          </>
        )}
      </Card>

      {toCancel && (
        <ConfirmDialog
          title="Cancel this reservation?"
          message={`${toCancel.userName}'s reservation for “${toCancel.bookTitle}” will be cancelled.${toCancel.status === 'READY' ? ' The copy being held for them goes to the next student in line.' : ''}`}
          confirmLabel="Cancel reservation"
          loading={cancel.isPending}
          onConfirm={() => cancel.mutate(toCancel.id)}
          onCancel={() => setToCancel(null)}
        />
      )}
    </>
  )
}
