import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { statisticsApi } from '../../api/admin'
import { loansApi } from '../../api/circulation'
import { useCurrentUser } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { StatCard } from '../../components/ui/StatCard'
import { describeDue, formatDate, formatMoney } from '../../lib/format'

const fresh = { staleTime: 0 }

/** The librarian's home: what needs doing at the desk today. */
export default function LibrarianDashboard() {
  const user = useCurrentUser()
  const overview = useQuery({ queryKey: ['dashboard', 'overview'], queryFn: statisticsApi.overview, ...fresh })
  const overdue = useQuery({
    queryKey: ['dashboard', 'overdue'],
    queryFn: () => loansApi.list({ scope: 'OVERDUE', size: 8 }),
    ...fresh,
  })
  const o = overview.data

  return (
    <>
      <PageHeader
        title={`Welcome, ${user.fullName}`}
        subtitle="What needs doing at the desk."
        actions={
          <Link to="/librarian/desk">
            <Button>Issue or return a book</Button>
          </Link>
        }
      />

      {overview.isError ? (
        <Card className="mb-6">
          <ErrorState error={overview.error} onRetry={() => overview.refetch()} />
        </Card>
      ) : (
        <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-busy={overview.isPending}>
          {overview.isPending || !o ? (
            Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-28" />)
          ) : (
            <>
              <StatCard label="Books on loan" value={o.circulation.activeLoans} hint={`${o.circulation.issuedToday} issued, ${o.circulation.returnedToday} returned today`} to="/librarian/desk" />
              <StatCard
                label="Overdue"
                value={o.circulation.overdueLoans}
                tone={o.circulation.overdueLoans > 0 ? 'danger' : 'default'}
                hint={o.circulation.overdueLoans > 0 ? 'Chase these up' : 'Nothing overdue'}
              />
              <StatCard
                label="Reservations"
                value={o.reservations.ready + o.reservations.pending}
                hint={`${o.reservations.ready} ready to collect, ${o.reservations.pending} waiting`}
                to="/librarian/reservations"
              />
              <StatCard
                label="Unpaid fines"
                value={formatMoney(o.fines.unpaidTotal)}
                tone={o.fines.unpaidCount > 0 ? 'warning' : 'default'}
                hint={`${o.fines.unpaidCount} ${o.fines.unpaidCount === 1 ? 'fine' : 'fines'}`}
                to="/librarian/fines"
              />
              <StatCard label="Copies available" value={o.catalogue.copiesAvailable} hint={`of ${o.catalogue.copiesInCirculation} in circulation`} to="/librarian/books" />
              <StatCard label="Titles" value={o.catalogue.titles} hint={`${o.catalogue.categories} categories`} to="/librarian/books" />
              <StatCard label="Seats in service" value={o.seats.inService} hint={`${o.seats.bookedNow} in use now`} to="/librarian/seats" />
              <StatCard label="Upcoming seat bookings" value={o.seats.upcomingBookings} hint={`${o.seats.bookingsStartingToday} start today`} />
            </>
          )}
        </div>
      )}

      <Card title="Overdue books">
        {overdue.isPending ? (
          <Skeleton className="h-24" />
        ) : overdue.isError ? (
          <ErrorState error={overdue.error} onRetry={() => overdue.refetch()} />
        ) : overdue.data.content.length === 0 ? (
          <p className="text-sm text-slate-500">No books are overdue.</p>
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {overdue.data.content.map((loan) => (
                <li key={loan.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
                  <div className="min-w-0">
                    <p className="truncate font-medium text-slate-900">{loan.bookTitle}</p>
                    <p className="text-sm text-slate-500">
                      {loan.userName} · copy {loan.copyBarcode} · was due {formatDate(loan.dueAt)}
                    </p>
                  </div>
                  <span className="text-sm font-medium text-red-700">{describeDue(loan.dueAt)}</span>
                </li>
              ))}
            </ul>
            {overdue.data.totalElements > overdue.data.content.length && (
              <p className="mt-3 text-sm text-slate-500">
                Showing the {overdue.data.content.length} longest overdue of {overdue.data.totalElements}.
              </p>
            )}
          </>
        )}
      </Card>
    </>
  )
}
