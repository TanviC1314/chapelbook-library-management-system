import type { Book, Category, Copy, Fine, Loan, Reservation, Seat, SeatBooking } from '../api/types'

/** Realistic API objects with sensible defaults; each test overrides only what it is about. */

export const computing: Category = { id: 1, name: 'Computing', description: null }
export const history: Category = { id: 2, name: 'History', description: null }

export function makeBook(overrides: Partial<Book> = {}): Book {
  return {
    id: 10,
    isbn: '9780132350884',
    title: 'Clean Code',
    author: 'Robert C. Martin',
    publisher: 'Prentice Hall',
    publicationYear: 2008,
    description: 'A handbook of agile software craftsmanship.',
    category: computing,
    totalCopies: 3,
    availableCopies: 2,
    pendingReservations: 0,
    ...overrides,
  }
}

export function makeCopy(overrides: Partial<Copy> = {}): Copy {
  return { id: 100, bookId: 10, barcode: 'CB-0001', status: 'AVAILABLE', shelfLocation: 'A1', ...overrides }
}

export function makeReservation(overrides: Partial<Reservation> = {}): Reservation {
  return {
    id: 50,
    bookId: 10,
    bookTitle: 'Clean Code',
    userId: 1,
    userName: 'Test student',
    status: 'PENDING',
    reservedAt: '2030-03-01T09:00:00Z',
    readyUntil: null,
    copyBarcode: null,
    queuePosition: 1,
    ...overrides,
  }
}

export function makeLoan(overrides: Partial<Loan> = {}): Loan {
  return {
    id: 70,
    bookId: 10,
    bookTitle: 'Clean Code',
    copyBarcode: 'CB-0001',
    userId: 1,
    userName: 'Test student',
    borrowedAt: '2030-03-01T09:00:00Z',
    dueAt: '2030-03-15T09:00:00Z',
    returnedAt: null,
    renewalCount: 0,
    status: 'ACTIVE',
    fineAmount: null,
    ...overrides,
  }
}

export function makeFine(overrides: Partial<Fine> = {}): Fine {
  return {
    id: 80,
    loanId: 70,
    bookTitle: 'Clean Code',
    userId: 1,
    userName: 'Test student',
    amount: 1.5,
    status: 'UNPAID',
    assessedAt: '2030-03-20T09:00:00Z',
    paidAt: null,
    ...overrides,
  }
}

export function makeSeat(overrides: Partial<Seat> = {}): Seat {
  return { id: 5, label: 'Q-01', zone: 'Quiet', hasPower: true, status: 'ACTIVE', available: null, ...overrides }
}

export function makeSeatBooking(overrides: Partial<SeatBooking> = {}): SeatBooking {
  return {
    id: 90,
    seatId: 5,
    seatLabel: 'Q-01',
    zone: 'Quiet',
    userId: 1,
    userName: 'Test student',
    startsAt: '2030-03-05T10:00:00Z',
    endsAt: '2030-03-05T12:00:00Z',
    status: 'BOOKED',
    ...overrides,
  }
}
