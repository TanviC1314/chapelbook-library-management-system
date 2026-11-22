import type { Book } from '../api/types'

export type AvailabilityTone = 'green' | 'amber' | 'slate'

/** One plain sentence about whether a student can get this book now, and how hard it is to get. */
export function describeAvailability(book: Pick<Book, 'totalCopies' | 'availableCopies' | 'pendingReservations'>): {
  text: string
  tone: AvailabilityTone
} {
  const { totalCopies, availableCopies, pendingReservations } = book
  if (totalCopies === 0) return { text: 'No copies in circulation', tone: 'slate' }
  if (availableCopies > 0) return { text: `${availableCopies} of ${totalCopies} available`, tone: 'green' }
  const waiting = pendingReservations > 0 ? `, ${pendingReservations} waiting` : ''
  const out = totalCopies === 1 ? 'The only copy is out' : 'All copies out'
  return { text: `${out}${waiting}`, tone: 'amber' }
}
