import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { statisticsApi } from '../../api/admin'
import { BarSeriesChart, COLORS } from '../../components/charts/Charts'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { TextField } from '../../components/ui/Field'
import { PageHeader } from '../../components/ui/PageHeader'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { StatCard } from '../../components/ui/StatCard'
import { checkDateRange, lastDays } from '../../lib/dateRange'
import { formatDay, formatHour, formatHours, formatMoney } from '../../lib/format'

const PRESETS = [
  { label: 'Last 7 days', days: 7 },
  { label: 'Last 30 days', days: 30 },
  { label: 'Last 90 days', days: 90 },
  { label: 'Last year', days: 365 },
]

/** Circulation and seat usage over any period up to a year. */
export default function AdminReportsPage() {
  const [range, setRange] = useState(() => lastDays(30))
  const problem = checkDateRange(range.from, range.to)
  const valid = problem === null

  const circulation = useQuery({
    queryKey: ['admin-report', 'circulation', range],
    queryFn: () => statisticsApi.circulation(range),
    enabled: valid,
  })
  const seats = useQuery({
    queryKey: ['admin-report', 'seats', range],
    queryFn: () => statisticsApi.seats(range),
    enabled: valid,
  })

  return (
    <>
      <PageHeader title="Reports" subtitle="How the library and its study seats are being used. Dates are in UTC." />

      <Card className="mb-6">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <TextField id="from" label="From" type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} />
          <TextField id="to" label="To" type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} />
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {PRESETS.map((preset) => (
            <Button key={preset.days} variant="secondary" onClick={() => setRange(lastDays(preset.days))}>
              {preset.label}
            </Button>
          ))}
        </div>
        {problem && (
          <div className="mt-4">
            <Alert>{problem}</Alert>
          </div>
        )}
      </Card>

      {valid && (
        <>
          <section aria-labelledby="circulation-heading" className="mb-10">
            <h2 id="circulation-heading" className="mb-4 text-lg font-semibold text-slate-900">
              Circulation
            </h2>
            {circulation.isPending ? (
              <Skeleton className="h-64" />
            ) : circulation.isError ? (
              <Card>
                <ErrorState error={circulation.error} onRetry={() => circulation.refetch()} />
              </Card>
            ) : (
              <div className="space-y-6">
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
                  <StatCard label="Loans issued" value={circulation.data.totals.loansIssued} />
                  <StatCard label="Loans returned" value={circulation.data.totals.loansReturned} />
                  <StatCard
                    label="Returned late"
                    value={circulation.data.totals.returnedLate}
                    tone={circulation.data.totals.returnedLate > 0 ? 'warning' : 'default'}
                  />
                  <StatCard label="Different borrowers" value={circulation.data.totals.distinctBorrowers} />
                  <StatCard
                    label="Fines raised"
                    value={formatMoney(circulation.data.totals.finesAmount)}
                    hint={`${circulation.data.totals.finesRaised} ${circulation.data.totals.finesRaised === 1 ? 'fine' : 'fines'}`}
                  />
                </div>

                <Card title="Loans each day">
                  <BarSeriesChart
                    xKey="day"
                    xHeader="Day"
                    ariaLabel="Bar chart of books issued and returned each day in the chosen period"
                    data={circulation.data.daily.map((d) => ({ day: formatDay(d.date), issued: d.loansIssued, returned: d.loansReturned }))}
                    series={[
                      { key: 'issued', label: 'Issued', color: COLORS.brand },
                      { key: 'returned', label: 'Returned', color: COLORS.green },
                    ]}
                  />
                </Card>

                <div className="grid gap-6 lg:grid-cols-2">
                  <Card title="Most borrowed books" className="p-0 [&>div]:p-0">
                    {circulation.data.topBooks.length === 0 ? (
                      <EmptyState title="No loans in this period" />
                    ) : (
                      <table className="w-full text-left text-sm">
                        <thead className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
                          <tr>
                            <th scope="col" className="px-5 py-3 font-medium">Title</th>
                            <th scope="col" className="px-5 py-3 text-right font-medium">Loans</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {circulation.data.topBooks.map((book) => (
                            <tr key={book.bookId}>
                              <td className="px-5 py-3">
                                <p className="font-medium text-slate-900">{book.title}</p>
                                <p className="text-xs text-slate-500">{book.author}</p>
                              </td>
                              <td className="px-5 py-3 text-right font-semibold text-slate-900">{book.loans}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </Card>

                  <Card title="Loans by category">
                    {circulation.data.byCategory.length === 0 ? (
                      <EmptyState title="No loans in this period" />
                    ) : (
                      <BarSeriesChart
                        xKey="category"
                        xHeader="Category"
                        ariaLabel="Bar chart of loans in each category in the chosen period"
                        data={circulation.data.byCategory.map((c) => ({ category: c.category, loans: c.loans }))}
                        series={[{ key: 'loans', label: 'Loans', color: COLORS.amber }]}
                      />
                    )}
                  </Card>
                </div>
              </div>
            )}
          </section>

          <section aria-labelledby="seats-heading">
            <h2 id="seats-heading" className="mb-4 text-lg font-semibold text-slate-900">
              Study seats
            </h2>
            {seats.isPending ? (
              <Skeleton className="h-64" />
            ) : seats.isError ? (
              <Card>
                <ErrorState error={seats.error} onRetry={() => seats.refetch()} />
              </Card>
            ) : (
              <div className="space-y-6">
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
                  <StatCard label="Seat bookings" value={seats.data.totals.bookings} hint="by start date" />
                  <StatCard label="Cancelled" value={seats.data.totals.cancelled} />
                  <StatCard label="Hours booked" value={formatHours(seats.data.totals.bookedHours)} />
                  <StatCard label="Different students" value={seats.data.totals.distinctStudents} />
                  <StatCard label="Seats in service" value={seats.data.totals.seatsInService} hint="right now" />
                </div>

                <Card title="Bookings each day">
                  <BarSeriesChart
                    xKey="day"
                    xHeader="Day"
                    ariaLabel="Bar chart of seat bookings starting each day in the chosen period"
                    data={seats.data.daily.map((d) => ({ day: formatDay(d.date), bookings: d.bookings }))}
                    series={[{ key: 'bookings', label: 'Bookings', color: COLORS.sky }]}
                  />
                </Card>

                <div className="grid gap-6 lg:grid-cols-2">
                  <Card title="Use by zone" className="p-0 [&>div]:p-0">
                    {seats.data.byZone.length === 0 ? (
                      <EmptyState title="There are no seats yet" />
                    ) : (
                      <table className="w-full text-left text-sm">
                        <thead className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
                          <tr>
                            <th scope="col" className="px-5 py-3 font-medium">Zone</th>
                            <th scope="col" className="px-3 py-3 text-right font-medium">Seats</th>
                            <th scope="col" className="px-3 py-3 text-right font-medium">Bookings</th>
                            <th scope="col" className="px-3 py-3 text-right font-medium">Hours</th>
                            <th scope="col" className="px-5 py-3 text-right font-medium">Hours per seat per day</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {seats.data.byZone.map((zone) => (
                            <tr key={zone.zone}>
                              <th scope="row" className="px-5 py-3 font-medium text-slate-900">{zone.zone}</th>
                              <td className="px-3 py-3 text-right text-slate-600">{zone.seatsInService}</td>
                              <td className="px-3 py-3 text-right text-slate-600">{zone.bookings}</td>
                              <td className="px-3 py-3 text-right text-slate-600">{formatHours(zone.bookedHours)}</td>
                              <td className="px-5 py-3 text-right font-semibold text-slate-900">{zone.averageHoursPerSeatPerDay.toFixed(2)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </Card>

                  <Card title="Busiest hours of the day">
                    <BarSeriesChart
                      xKey="hour"
                      xHeader="Hour"
                      ariaLabel="Bar chart of how many seat bookings were active in each hour of the day"
                      data={seats.data.peakHours.map((h) => ({ hour: formatHour(h.hour), bookings: h.bookings }))}
                      series={[{ key: 'bookings', label: 'Active bookings', color: COLORS.brand }]}
                    />
                  </Card>
                </div>
              </div>
            )}
          </section>
        </>
      )}
    </>
  )
}
