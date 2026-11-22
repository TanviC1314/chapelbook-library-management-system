import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { finesApi, loansApi, reservationsApi } from '../../api/circulation'
import { seatsApi } from '../../api/seats'
import { useCurrentUser } from '../../auth/AuthContext'
import { Alert } from '../../components/ui/Alert'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { count, describeDue, formatDate, formatMoney, formatTime } from '../../lib/format'

// The dashboard is the first page after sign-in, so it always asks again rather than showing numbers from a minute ago.
const fresh = { staleTime: 0 }

function More({ to, children }: { to: string; children: string }) {
  return (
    <Link to={to} className="mt-4 inline-block text-sm font-medium text-brand-700 hover:underline">
      {children}
    </Link>
  )
}

export default function StudentDashboard() {
  const user = useCurrentUser()
  const loans = useQuery({ queryKey: ['dashboard', 'loans'], queryFn: () => loansApi.mine({ activeOnly: true, size: 50 }), ...fresh })
  const reservations = useQuery({ queryKey: ['dashboard', 'reservations'], queryFn: () => reservationsApi.mine({ size: 50 }), ...fresh })
  const balance = useQuery({ queryKey: ['dashboard', 'balance'], queryFn: finesApi.balance, ...fresh })
  const bookings = useQuery({ queryKey: ['dashboard', 'seats'], queryFn: () => seatsApi.mine({ upcomingOnly: true, size: 3 }), ...fresh })

  const active = loans.data?.content ?? []
  const overdue = active.filter((l) => l.status === 'OVERDUE')
  const dueSoon = [...active].sort((a, b) => a.dueAt.localeCompare(b.dueAt)).slice(0, 3)
  const ready = (reservations.data?.content ?? []).filter((r) => r.status === 'READY')
  const waiting = (reservations.data?.content ?? []).filter((r) => r.status === 'PENDING')

  return (
    <>
      <PageHeader title={`Welcome, ${user.fullName}`} subtitle="Here is what needs your attention." />

      <div className="mb-6 space-y-3">
        {overdue.length > 0 && (
          <Alert>
            You have {count(overdue.length, 'overdue book')}. Please return {overdue.length === 1 ? 'it' : 'them'} to the desk: a fine is charged for each
            day late.
          </Alert>
        )}
        {ready.length > 0 && (
          <Alert kind="success">
            {ready.length === 1 ? (
              <>
                “{ready[0].bookTitle}” is ready to collect from the desk until {formatDate(ready[0].readyUntil!)}.
              </>
            ) : (
              <>{ready.length} reserved books are ready to collect from the desk.</>
            )}
          </Alert>
        )}
        {balance.data?.blocked && (
          <Alert>
            Your unpaid fines are {formatMoney(balance.data.unpaid)}, so you cannot borrow new books until they are {formatMoney(balance.data.borrowingBlockedAbove)}{' '}
            or less.
          </Alert>
        )}
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card title="Books on loan">
          {loans.isPending ? (
            <Skeleton className="h-20" />
          ) : loans.isError ? (
            <ErrorState error={loans.error} onRetry={() => loans.refetch()} />
          ) : active.length === 0 ? (
            <p className="text-sm text-slate-500">You have no books on loan.</p>
          ) : (
            <>
              <p className="text-3xl font-semibold text-slate-900">{loans.data.totalElements}</p>
              <ul className="mt-3 space-y-2">
                {dueSoon.map((loan) => (
                  <li key={loan.id} className="flex justify-between gap-3 text-sm">
                    <span className="truncate text-slate-800">{loan.bookTitle}</span>
                    <span className={`shrink-0 ${loan.status === 'OVERDUE' ? 'font-medium text-red-700' : 'text-slate-500'}`}>{describeDue(loan.dueAt)}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
          <More to="/student/borrowings">See my borrowings</More>
        </Card>

        <Card title="Reservations">
          {reservations.isPending ? (
            <Skeleton className="h-20" />
          ) : reservations.isError ? (
            <ErrorState error={reservations.error} onRetry={() => reservations.refetch()} />
          ) : ready.length + waiting.length === 0 ? (
            <p className="text-sm text-slate-500">You have no active reservations.</p>
          ) : (
            <ul className="space-y-2 text-sm">
              <li>
                <span className="text-2xl font-semibold text-slate-900">{ready.length}</span> ready to collect
              </li>
              <li>
                <span className="text-2xl font-semibold text-slate-900">{waiting.length}</span> waiting in the queue
              </li>
            </ul>
          )}
          <More to="/student/reservations">See my reservations</More>
        </Card>

        <Card title="Fines">
          {balance.isPending ? (
            <Skeleton className="h-12" />
          ) : balance.isError ? (
            <ErrorState error={balance.error} onRetry={() => balance.refetch()} />
          ) : (
            <>
              <p className="text-3xl font-semibold text-slate-900">{formatMoney(balance.data.unpaid)}</p>
              <p className="mt-1 text-sm text-slate-500">{balance.data.unpaid > 0 ? 'Unpaid. Pay at the library desk.' : 'No unpaid fines.'}</p>
            </>
          )}
          <More to="/student/fines">See my fines</More>
        </Card>

        <Card title="Upcoming seat bookings">
          {bookings.isPending ? (
            <Skeleton className="h-20" />
          ) : bookings.isError ? (
            <ErrorState error={bookings.error} onRetry={() => bookings.refetch()} />
          ) : bookings.data.content.length === 0 ? (
            <p className="text-sm text-slate-500">You have no upcoming seat bookings.</p>
          ) : (
            <ul className="space-y-2 text-sm">
              {bookings.data.content.map((b) => (
                <li key={b.id} className="flex justify-between gap-3">
                  <span className="text-slate-800">Seat {b.seatLabel}</span>
                  <span className="text-slate-500">
                    {formatDate(b.startsAt)}, {formatTime(b.startsAt)} to {formatTime(b.endsAt)}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <More to="/student/seats">Book or manage seats</More>
        </Card>
      </div>
    </>
  )
}
