import type { Book } from '../../api/types'
import { describeAvailability, type AvailabilityTone } from '../../lib/availability'

const tones: Record<AvailabilityTone, string> = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  amber: 'bg-amber-50 text-amber-800 ring-amber-600/20',
  slate: 'bg-slate-100 text-slate-600 ring-slate-500/20',
}

export function AvailabilityPill({ book }: { book: Pick<Book, 'totalCopies' | 'availableCopies' | 'pendingReservations'> }) {
  const { text, tone } = describeAvailability(book)
  return <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${tones[tone]}`}>{text}</span>
}
