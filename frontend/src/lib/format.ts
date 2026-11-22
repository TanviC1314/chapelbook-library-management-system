/** Display formatting kept in one place, so every page shows dates, money and plurals the same way. */

/** The currency fines are shown in. The backend stores plain amounts; this is only how they are displayed. */
export const CURRENCY = 'EUR'

/** Pluralises the simple English nouns the app uses: entry to entries, book to books, class to classes. */
export function plural(noun: string): string {
  if (/[^aeiou]y$/i.test(noun)) return `${noun.slice(0, -1)}ies`
  if (/(s|x|z|ch|sh)$/i.test(noun)) return `${noun}es`
  return `${noun}s`
}

/** "3 books", "1 book". */
export function count(n: number, noun: string): string {
  return `${n} ${n === 1 ? noun : plural(noun)}`
}

export function formatMoney(amount: number): string {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: CURRENCY }).format(amount)
}

/** Dates and times are shown in the viewer's own time zone unless one is given (tests pass UTC). */
export function formatDate(iso: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', timeZone }).format(new Date(iso))
}

export function formatDateTime(iso: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone,
  }).format(new Date(iso))
}

export function formatTime(iso: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', timeZone }).format(new Date(iso))
}

const DAY_MS = 24 * 60 * 60 * 1000

/** Whole days from now until the given moment, rounded up; negative once it has passed. */
export function daysUntil(iso: string, now: number = Date.now()): number {
  return Math.ceil((new Date(iso).getTime() - now) / DAY_MS)
}

/** "due today", "due in 3 days", "2 days overdue". */
export function describeDue(iso: string, now: number = Date.now()): string {
  const days = daysUntil(iso, now)
  if (days > 1) return `due in ${days} days`
  if (days === 1) return 'due tomorrow'
  if (days === 0) return 'due today'
  const late = Math.abs(days)
  return `${late} ${late === 1 ? 'day' : 'days'} overdue`
}
