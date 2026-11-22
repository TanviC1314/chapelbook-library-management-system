/** Date ranges for the admin reports. Dates are plain yyyy-MM-dd strings, the way the backend takes them. */

/** The longest range the backend will report on. */
export const MAX_RANGE_DAYS = 366

const DAY_MS = 86_400_000

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

export function toDateString(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** The last `days` days ending today, inclusive of today. */
export function lastDays(days: number, today: Date = new Date()): { from: string; to: string } {
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1))
  return { from: toDateString(from), to: toDateString(today) }
}

/** Whole days covered by an inclusive range, or NaN if either date is not valid. */
export function daysInRange(from: string, to: string): number {
  const start = Date.parse(`${from}T00:00:00Z`)
  const end = Date.parse(`${to}T00:00:00Z`)
  return Math.round((end - start) / DAY_MS) + 1
}

/** A message for the person if the range cannot be reported on, or null if it is fine. */
export function checkDateRange(from: string, to: string): string | null {
  if (!from || !to) return 'Choose a start and an end date.'
  const days = daysInRange(from, to)
  if (Number.isNaN(days)) return 'Enter valid dates.'
  if (days < 1) return 'The end date must not be before the start date.'
  if (days > MAX_RANGE_DAYS) return `A report can cover at most ${MAX_RANGE_DAYS} days.`
  return null
}
