import { describe, expect, it } from 'vitest'
import { checkSeatWindow, defaultWindow, toLocalInputValue } from './seatWindow'

// All times here are built and read in the machine's own time zone, so the tests give the same answer anywhere.
const now = new Date(2030, 2, 10, 12, 0, 0) // 10 March 2030, 12:00 local
const at = (day: number, hour: number, minute = 0) => toLocalInputValue(new Date(2030, 2, day, hour, minute))

describe('toLocalInputValue', () => {
  it('formats the way a datetime-local input expects, with zero padding', () => {
    expect(toLocalInputValue(new Date(2030, 0, 5, 9, 7))).toBe('2030-01-05T09:07')
  })
})

describe('defaultWindow', () => {
  it('starts at the next whole hour and lasts two hours', () => {
    expect(defaultWindow(new Date(2030, 2, 10, 12, 30))).toEqual({ start: at(10, 13), end: at(10, 15) })
  })

  it('moves to the next day when the next hour is after midnight', () => {
    expect(defaultWindow(new Date(2030, 2, 10, 23, 15))).toEqual({ start: at(11, 0), end: at(11, 2) })
  })

  it('is always an acceptable window', () => {
    for (const hour of [0, 5, 12, 23]) {
      const { start, end } = defaultWindow(new Date(2030, 2, 10, hour, 40))

      expect(checkSeatWindow(start, end, new Date(2030, 2, 10, hour, 40)).error).toBeNull()
    }
  })
})

describe('checkSeatWindow', () => {
  it('accepts a normal booking and returns UTC instants', () => {
    const result = checkSeatWindow(at(11, 10), at(11, 12), now)

    expect(result.error).toBeNull()
    expect(result.startsAt).toBe(new Date(2030, 2, 11, 10).toISOString())
    expect(result.endsAt).toBe(new Date(2030, 2, 11, 12).toISOString())
  })

  it('asks for both times', () => {
    expect(checkSeatWindow('', at(11, 12), now).error).toBe('Choose a start and an end time.')
    expect(checkSeatWindow(at(11, 10), '', now).error).toBe('Choose a start and an end time.')
  })

  it('rejects text that is not a date', () => {
    expect(checkSeatWindow('nonsense', at(11, 12), now).error).toBe('Enter a valid start and end time.')
  })

  it('requires the end to be after the start', () => {
    expect(checkSeatWindow(at(11, 12), at(11, 10), now).error).toBe('The booking must end after it starts.')
    expect(checkSeatWindow(at(11, 12), at(11, 12), now).error).toBe('The booking must end after it starts.')
  })

  it('enforces the 15 minute minimum exactly', () => {
    expect(checkSeatWindow(at(11, 10), at(11, 10, 14), now).error).toBe('A booking must be at least 15 minutes.')
    expect(checkSeatWindow(at(11, 10), at(11, 10, 15), now).error).toBeNull()
  })

  it('enforces the 8 hour maximum exactly', () => {
    expect(checkSeatWindow(at(11, 8), at(11, 16), now).error).toBeNull()
    expect(checkSeatWindow(at(11, 8), at(11, 16, 1), now).error).toBe('A booking can be at most 8 hours.')
  })

  it('does not allow starting in the past, but forgives the last minute', () => {
    expect(checkSeatWindow(at(10, 9), at(10, 11), now).error).toBe('A booking cannot start in the past.')
    expect(checkSeatWindow(at(10, 11, 59), at(10, 13), now).error).toBeNull()
    expect(checkSeatWindow(at(10, 11, 58), at(10, 13), now).error).toBe('A booking cannot start in the past.')
  })

  it('does not allow booking more than 14 days ahead', () => {
    expect(checkSeatWindow(at(24, 12), at(24, 14), now).error).toBeNull() // exactly 14 days
    expect(checkSeatWindow(at(24, 12, 1), at(24, 14), now).error).toBe('Seats can only be booked up to 14 days ahead.')
    expect(checkSeatWindow(at(30, 10), at(30, 12), now).error).toBe('Seats can only be booked up to 14 days ahead.')
  })
})
