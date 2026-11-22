import type { Overview } from '../../api/types'
import { StatCard } from '../../components/ui/StatCard'
import { formatMoney } from '../../lib/format'

/** The live headline numbers shared by the librarian and admin dashboards, each linking to the page behind it. */
export function OverviewCards({ overview: o }: { overview: Overview }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatCard
        label="Books on loan"
        value={o.circulation.activeLoans}
        hint={`${o.circulation.issuedToday} issued, ${o.circulation.returnedToday} returned today`}
        to="/librarian/desk"
      />
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
    </div>
  )
}
