type Tone = 'green' | 'amber' | 'red' | 'blue' | 'slate'

const tones: Record<Tone, string> = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  amber: 'bg-amber-50 text-amber-800 ring-amber-600/20',
  red: 'bg-red-50 text-red-700 ring-red-600/20',
  blue: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  slate: 'bg-slate-100 text-slate-700 ring-slate-500/20',
}

/** Every status the backend can send, in words a person would use, with a colour that signals how it is going. */
const statuses: Record<string, { label: string; tone: Tone }> = {
  // copies
  AVAILABLE: { label: 'Available', tone: 'green' },
  ON_LOAN: { label: 'On loan', tone: 'blue' },
  HELD: { label: 'Held for a student', tone: 'amber' },
  LOST: { label: 'Lost', tone: 'red' },
  WITHDRAWN: { label: 'Withdrawn', tone: 'slate' },
  // reservations
  PENDING: { label: 'In the queue', tone: 'amber' },
  READY: { label: 'Ready to collect', tone: 'green' },
  FULFILLED: { label: 'Collected', tone: 'slate' },
  CANCELLED: { label: 'Cancelled', tone: 'slate' },
  EXPIRED: { label: 'Expired', tone: 'slate' },
  // loans
  ACTIVE: { label: 'On loan', tone: 'blue' },
  OVERDUE: { label: 'Overdue', tone: 'red' },
  RETURNED: { label: 'Returned', tone: 'slate' },
  // fines
  UNPAID: { label: 'Unpaid', tone: 'red' },
  PAID: { label: 'Paid', tone: 'green' },
  WAIVED: { label: 'Waived', tone: 'slate' },
  // seats and bookings
  OUT_OF_SERVICE: { label: 'Out of service', tone: 'red' },
  BOOKED: { label: 'Booked', tone: 'blue' },
}

export function statusLabel(status: string): string {
  return statuses[status]?.label ?? status
}

export function StatusBadge({ status }: { status: string }) {
  const { label, tone } = statuses[status] ?? { label: status, tone: 'slate' as Tone }
  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${tones[tone]}`}>{label}</span>
}
