import type { CirculationStats, SeatStats } from '../api/types'

/** Three days of circulation, enough to see zeros, ordering and totals. */
export function makeCirculationStats(overrides: Partial<CirculationStats> = {}): CirculationStats {
  return {
    from: '2030-03-01',
    to: '2030-03-03',
    totals: { loansIssued: 5, loansReturned: 3, returnedLate: 1, distinctBorrowers: 4, finesRaised: 1, finesAmount: 0.75 },
    daily: [
      { date: '2030-03-01', loansIssued: 2, loansReturned: 0, finesRaised: 0, finesAmount: 0 },
      { date: '2030-03-02', loansIssued: 0, loansReturned: 1, finesRaised: 0, finesAmount: 0 },
      { date: '2030-03-03', loansIssued: 3, loansReturned: 2, finesRaised: 1, finesAmount: 0.75 },
    ],
    topBooks: [
      { bookId: 1, title: 'Clean Code', author: 'Robert C. Martin', loans: 3 },
      { bookId: 2, title: 'Sapiens', author: 'Yuval Harari', loans: 2 },
    ],
    byCategory: [
      { categoryId: 1, category: 'Computing', loans: 3 },
      { categoryId: 2, category: 'History', loans: 2 },
    ],
    ...overrides,
  }
}

export function makeSeatStats(overrides: Partial<SeatStats> = {}): SeatStats {
  return {
    from: '2030-03-01',
    to: '2030-03-03',
    totals: { bookings: 6, cancelled: 1, bookedHours: 11.5, distinctStudents: 4, seatsInService: 10 },
    daily: [
      { date: '2030-03-01', bookings: 1, bookedHours: 2 },
      { date: '2030-03-02', bookings: 3, bookedHours: 6.5 },
      { date: '2030-03-03', bookings: 2, bookedHours: 3 },
    ],
    byZone: [
      { zone: 'Quiet', seatsInService: 6, bookings: 4, bookedHours: 8.5, averageHoursPerSeatPerDay: 0.47 },
      { zone: 'Group', seatsInService: 4, bookings: 2, bookedHours: 3, averageHoursPerSeatPerDay: 0.25 },
    ],
    peakHours: Array.from({ length: 24 }, (_, hour) => ({ hour, bookings: hour === 10 ? 3 : hour === 14 ? 2 : 0 })),
    ...overrides,
  }
}
