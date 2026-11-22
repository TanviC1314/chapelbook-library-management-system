import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { statisticsApi } from '../../api/admin'
import { useCurrentUser } from '../../auth/AuthContext'
import { BarSeriesChart, COLORS, LineSeriesChart } from '../../components/charts/Charts'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { PageHeader } from '../../components/ui/PageHeader'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { StatCard } from '../../components/ui/StatCard'
import { lastDays } from '../../lib/dateRange'
import { formatDay } from '../../lib/format'
import { OverviewCards } from '../dashboard/OverviewCards'

const fresh = { staleTime: 0 }

/** The administrator's home: the whole library at a glance, plus the last month's trends. */
export default function AdminDashboard() {
  const user = useCurrentUser()
  const range = lastDays(30)
  const overview = useQuery({ queryKey: ['dashboard', 'overview'], queryFn: statisticsApi.overview, ...fresh })
  const circulation = useQuery({ queryKey: ['dashboard', 'circulation', range], queryFn: () => statisticsApi.circulation(range), ...fresh })
  const seats = useQuery({ queryKey: ['dashboard', 'seat-stats', range], queryFn: () => statisticsApi.seats(range), ...fresh })
  const o = overview.data

  return (
    <>
      <PageHeader
        title={`Welcome, ${user.fullName}`}
        subtitle="The library at a glance."
        actions={
          <Link to="/admin/reports">
            <Button variant="secondary">Full reports</Button>
          </Link>
        }
      />

      {overview.isError ? (
        <Card className="mb-6">
          <ErrorState error={overview.error} onRetry={() => overview.refetch()} />
        </Card>
      ) : overview.isPending || !o ? (
        <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-busy="true" aria-label="Loading figures">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
      ) : (
        <div className="mb-6 space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Students" value={o.users.students} hint="active accounts" to="/admin/users?role=STUDENT" />
            <StatCard label="Librarians" value={o.users.librarians} hint="active accounts" to="/admin/users?role=LIBRARIAN" />
            <StatCard label="Administrators" value={o.users.admins} hint="active accounts" to="/admin/users?role=ADMIN" />
            <StatCard label="Deactivated accounts" value={o.users.deactivated} tone={o.users.deactivated > 0 ? 'warning' : 'default'} to="/admin/users?state=deactivated" />
          </div>
          <OverviewCards overview={o} />
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Loans over the last 30 days">
          {circulation.isPending ? (
            <Skeleton className="h-64" />
          ) : circulation.isError ? (
            <ErrorState error={circulation.error} onRetry={() => circulation.refetch()} />
          ) : (
            <LineSeriesChart
              xKey="day"
              xHeader="Day"
              ariaLabel="Line chart of books issued and returned each day over the last 30 days"
              data={circulation.data.daily.map((d) => ({ day: formatDay(d.date), issued: d.loansIssued, returned: d.loansReturned }))}
              series={[
                { key: 'issued', label: 'Issued', color: COLORS.brand },
                { key: 'returned', label: 'Returned', color: COLORS.green },
              ]}
            />
          )}
        </Card>

        <Card title="Seat bookings over the last 30 days">
          {seats.isPending ? (
            <Skeleton className="h-64" />
          ) : seats.isError ? (
            <ErrorState error={seats.error} onRetry={() => seats.refetch()} />
          ) : (
            <BarSeriesChart
              xKey="day"
              xHeader="Day"
              ariaLabel="Bar chart of seat bookings starting each day over the last 30 days"
              data={seats.data.daily.map((d) => ({ day: formatDay(d.date), bookings: d.bookings }))}
              series={[{ key: 'bookings', label: 'Bookings', color: COLORS.sky }]}
            />
          )}
        </Card>
      </div>
    </>
  )
}
