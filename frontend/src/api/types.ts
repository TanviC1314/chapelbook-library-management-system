// Shapes of the backend's JSON. They mirror the response records in the Spring Boot API one to one.
// Instants arrive as ISO-8601 strings in UTC and money as plain numbers with two decimal places.

export type Role = 'STUDENT' | 'LIBRARIAN' | 'ADMIN'

export interface User {
  id: number
  email: string
  fullName: string
  role: Role
  studentNumber: string | null
  active: boolean
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: User
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Category {
  id: number
  name: string
  description: string | null
}

export interface Book {
  id: number
  isbn: string | null
  title: string
  author: string
  publisher: string | null
  publicationYear: number | null
  description: string | null
  category: Category
  totalCopies: number
  availableCopies: number
  pendingReservations: number
}

export type CopyStatus = 'AVAILABLE' | 'ON_LOAN' | 'HELD' | 'LOST' | 'WITHDRAWN'

export interface Copy {
  id: number
  bookId: number
  barcode: string
  status: CopyStatus
  shelfLocation: string | null
}

export type ReservationStatus = 'PENDING' | 'READY' | 'FULFILLED' | 'CANCELLED' | 'EXPIRED'

export interface Reservation {
  id: number
  bookId: number
  bookTitle: string
  userId: number
  userName: string
  status: ReservationStatus
  reservedAt: string
  readyUntil: string | null
  copyBarcode: string | null
  queuePosition: number | null
}

export type LoanStatus = 'ACTIVE' | 'OVERDUE' | 'RETURNED'

export interface Loan {
  id: number
  bookId: number
  bookTitle: string
  copyBarcode: string
  userId: number
  userName: string
  borrowedAt: string
  dueAt: string
  returnedAt: string | null
  renewalCount: number
  status: LoanStatus
  fineAmount: number | null
}

export type FineStatus = 'UNPAID' | 'PAID' | 'WAIVED'

export interface Fine {
  id: number
  loanId: number
  bookTitle: string
  userId: number
  userName: string
  amount: number
  status: FineStatus
  assessedAt: string
  paidAt: string | null
}

export interface FineBalance {
  unpaid: number
  borrowingBlockedAbove: number
  blocked: boolean
}

export type SeatStatus = 'ACTIVE' | 'OUT_OF_SERVICE'

export interface Seat {
  id: number
  label: string
  zone: string
  hasPower: boolean
  status: SeatStatus
  /** Only present when the request asked about a time window. */
  available: boolean | null
}

export type SeatBookingStatus = 'BOOKED' | 'CANCELLED'

export interface SeatBooking {
  id: number
  seatId: number
  seatLabel: string
  zone: string
  userId: number
  userName: string
  startsAt: string
  endsAt: string
  status: SeatBookingStatus
}

export interface Overview {
  generatedAt: string
  users: { students: number; librarians: number; admins: number; deactivated: number }
  catalogue: {
    titles: number
    categories: number
    copiesInCirculation: number
    copiesAvailable: number
    copiesOnLoan: number
    copiesHeld: number
    copiesLost: number
    copiesWithdrawn: number
  }
  circulation: { activeLoans: number; overdueLoans: number; issuedToday: number; returnedToday: number }
  reservations: { pending: number; ready: number }
  fines: { unpaidCount: number; unpaidTotal: number }
  seats: { inService: number; outOfService: number; bookedNow: number; upcomingBookings: number; bookingsStartingToday: number }
}

export interface CirculationStats {
  from: string
  to: string
  totals: {
    loansIssued: number
    loansReturned: number
    returnedLate: number
    distinctBorrowers: number
    finesRaised: number
    finesAmount: number
  }
  daily: { date: string; loansIssued: number; loansReturned: number; finesRaised: number; finesAmount: number }[]
  topBooks: { bookId: number; title: string; author: string; loans: number }[]
  byCategory: { categoryId: number; category: string; loans: number }[]
}

export interface SeatStats {
  from: string
  to: string
  totals: { bookings: number; cancelled: number; bookedHours: number; distinctStudents: number; seatsInService: number }
  daily: { date: string; bookings: number; bookedHours: number }[]
  byZone: { zone: string; seatsInService: number; bookings: number; bookedHours: number; averageHoursPerSeatPerDay: number }[]
  peakHours: { hour: number; bookings: number }[]
}
