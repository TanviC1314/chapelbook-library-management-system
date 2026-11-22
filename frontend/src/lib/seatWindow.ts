/**
 * Checks a seat booking window in the browser so people get an instant message instead of a round trip.
 * These limits mirror the backend's defaults (15 minutes to 8 hours, up to 14 days ahead). The backend stays the
 * authority: if its settings differ, its message is shown.
 */
export const MIN_MINUTES = 15
export const MAX_HOURS = 8
export const MAX_DAYS_AHEAD = 14

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
const START_GRACE = MINUTE

export interface WindowCheck {
  /** A message for the person, or null when the window is acceptable. */
  error: string | null
  /** ISO-8601 instants in UTC, present only when the window is acceptable. */
  startsAt?: string
  endsAt?: string
}

/** The value a datetime-local input expects: local time as yyyy-MM-ddTHH:mm. */
export function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** A sensible starting point: the next whole hour, for two hours. */
export function defaultWindow(now: Date = new Date()): { start: string; end: string } {
  const start = new Date(now)
  start.setMinutes(0, 0, 0)
  start.setHours(start.getHours() + 1)
  const end = new Date(start.getTime() + 2 * HOUR)
  return { start: toLocalInputValue(start), end: toLocalInputValue(end) }
}

export function checkSeatWindow(startLocal: string, endLocal: string, now: Date = new Date()): WindowCheck {
  if (!startLocal || !endLocal) return { error: 'Choose a start and an end time.' }
  const start = new Date(startLocal)
  const end = new Date(endLocal)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return { error: 'Enter a valid start and end time.' }
  if (end.getTime() <= start.getTime()) return { error: 'The booking must end after it starts.' }

  const length = end.getTime() - start.getTime()
  if (length < MIN_MINUTES * MINUTE) return { error: `A booking must be at least ${MIN_MINUTES} minutes.` }
  if (length > MAX_HOURS * HOUR) return { error: `A booking can be at most ${MAX_HOURS} hours.` }
  if (start.getTime() < now.getTime() - START_GRACE) return { error: 'A booking cannot start in the past.' }
  if (start.getTime() > now.getTime() + MAX_DAYS_AHEAD * DAY) {
    return { error: `Seats can only be booked up to ${MAX_DAYS_AHEAD} days ahead.` }
  }
  return { error: null, startsAt: start.toISOString(), endsAt: end.toISOString() }
}
