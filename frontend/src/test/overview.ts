import type { Overview } from '../api/types'

export function makeOverview(overrides: Partial<Overview> = {}): Overview {
  return {
    generatedAt: '2030-03-10T12:00:00Z',
    users: { students: 120, librarians: 4, admins: 2, deactivated: 3 },
    catalogue: { titles: 540, categories: 12, copiesInCirculation: 900, copiesAvailable: 640, copiesOnLoan: 230, copiesHeld: 30, copiesLost: 5, copiesWithdrawn: 8 },
    circulation: { activeLoans: 230, overdueLoans: 14, issuedToday: 21, returnedToday: 17 },
    reservations: { pending: 9, ready: 6 },
    fines: { unpaidCount: 11, unpaidTotal: 42.75 },
    seats: { inService: 60, outOfService: 2, bookedNow: 18, upcomingBookings: 75, bookingsStartingToday: 31 },
    ...overrides,
  }
}
