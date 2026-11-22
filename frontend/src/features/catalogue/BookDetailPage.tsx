import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { catalogueApi } from '../../api/catalogue'
import { reservationsApi } from '../../api/circulation'
import { describeError } from '../../api/client'
import type { Reservation } from '../../api/types'
import { useCurrentUser } from '../../auth/AuthContext'
import { AvailabilityPill } from '../../components/ui/AvailabilityPill'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toast'
import { formatDate } from '../../lib/format'

export default function BookDetailPage() {
  const { id } = useParams()
  const bookId = Number(id)
  const user = useCurrentUser()
  const toast = useToast()
  const queryClient = useQueryClient()
  const isStudent = user.role === 'STUDENT'

  const book = useQuery({ queryKey: ['book', bookId], queryFn: () => catalogueApi.getBook(bookId), enabled: Number.isFinite(bookId) })

  // Does this student already have a live reservation for the title?
  const mine = useQuery({
    queryKey: ['my-reservations', 'active'],
    queryFn: () => reservationsApi.mine({ size: 100 }),
    enabled: isStudent,
  })
  const existing: Reservation | undefined = mine.data?.content.find(
    (r) => r.bookId === bookId && (r.status === 'PENDING' || r.status === 'READY'),
  )

  const reserve = useMutation({
    mutationFn: () => reservationsApi.reserve(bookId),
    onSuccess: (reservation) => {
      toast.success(
        reservation.status === 'READY'
          ? `Reserved. A copy is being held for you until ${formatDate(reservation.readyUntil!)}.`
          : `You joined the queue at position ${reservation.queuePosition}.`,
      )
      void queryClient.invalidateQueries({ queryKey: ['my-reservations'] })
      void queryClient.invalidateQueries({ queryKey: ['book', bookId] })
      void queryClient.invalidateQueries({ queryKey: ['books'] })
    },
    onError: (error) => toast.error(describeError(error)),
  })

  if (book.isPending) return <Skeleton className="h-64" />
  if (book.isError) {
    return (
      <Card>
        <ErrorState error={book.error} onRetry={() => book.refetch()} />
      </Card>
    )
  }

  const b = book.data
  const canTakeNow = b.availableCopies > 0

  return (
    <>
      <Link to="/catalogue" className="mb-4 inline-flex items-center gap-1 text-sm text-brand-700 hover:underline">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Back to the catalogue
      </Link>
      <PageHeader title={b.title} subtitle={b.author} />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="About this book" className="lg:col-span-2">
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Category" value={b.category.name} />
            <Detail label="Published" value={b.publicationYear ? String(b.publicationYear) : null} />
            <Detail label="Publisher" value={b.publisher} />
            <Detail label="ISBN" value={b.isbn} />
          </dl>
          {b.description && <p className="mt-4 text-sm leading-relaxed text-slate-700">{b.description}</p>}
        </Card>

        <Card title="Availability">
          <AvailabilityPill book={b} />
          <p className="mt-2 text-sm text-slate-500">
            {b.pendingReservations > 0
              ? `${b.pendingReservations} ${b.pendingReservations === 1 ? 'student is' : 'students are'} waiting for this book.`
              : 'Nobody is waiting for this book.'}
          </p>

          {isStudent && (
            <div className="mt-5">
              {existing?.status === 'READY' ? (
                <p role="status" className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                  Your copy is ready to collect from the desk until {formatDate(existing.readyUntil!)}.
                </p>
              ) : existing?.status === 'PENDING' ? (
                <p role="status" className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  You are number {existing.queuePosition} in the queue.
                </p>
              ) : b.totalCopies === 0 ? (
                <p className="text-sm text-slate-500">There are no copies to reserve yet.</p>
              ) : (
                <Button className="w-full" loading={reserve.isPending} onClick={() => reserve.mutate()}>
                  {canTakeNow ? 'Reserve a copy' : 'Join the queue'}
                </Button>
              )}
              {existing && (
                <Link to="/student/reservations" className="mt-3 block text-sm font-medium text-brand-700 hover:underline">
                  Manage my reservations
                </Link>
              )}
            </div>
          )}
        </Card>
      </div>
    </>
  )
}

function Detail({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-medium text-slate-900">{value ?? '—'}</dd>
    </div>
  )
}
