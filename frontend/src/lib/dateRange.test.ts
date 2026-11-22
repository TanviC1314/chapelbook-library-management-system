import { describe, expect, it } from 'vitest'
import { checkDateRange, daysInRange, lastDays, toDateString } from './dateRange'

describe('toDateString', () => {
  it('pads month and day', () => {
    expect(toDateString(new Date(2030, 0, 5))).toBe('2030-01-05')
  })
})

describe('lastDays', () => {
  const today = new Date(2030, 2, 10) // 10 March 2030, local

  it('ends today and counts today as one of the days', () => {
    expect(lastDays(30, today)).toEqual({ from: '2030-02-09', to: '2030-03-10' })
    expect(daysInRange(lastDays(30, today).from, lastDays(30, today).to)).toBe(30)
  })

  it('handles a single day and crossing a year boundary', () => {
    expect(lastDays(1, today)).toEqual({ from: '2030-03-10', to: '2030-03-10' })
    expect(lastDays(10, new Date(2030, 0, 3))).toEqual({ from: '2029-12-25', to: '2030-01-03' })
  })
})

describe('daysInRange', () => {
  it('is inclusive of both ends', () => {
    expect(daysInRange('2030-03-01', '2030-03-01')).toBe(1)
    expect(daysInRange('2030-03-01', '2030-03-05')).toBe(5)
  })

  it('counts a leap day', () => {
    expect(daysInRange('2024-02-28', '2024-03-01')).toBe(3)
  })
})

describe('checkDateRange', () => {
  it('accepts a normal range and the largest allowed one', () => {
    expect(checkDateRange('2030-03-01', '2030-03-31')).toBeNull()
    expect(checkDateRange('2019-12-31', '2020-12-30')).toBeNull() // exactly 366 days
  })

  it('refuses a range over 366 days', () => {
    expect(checkDateRange('2019-12-31', '2020-12-31')).toBe('A report can cover at most 366 days.')
  })

  it('refuses an end before the start', () => {
    expect(checkDateRange('2030-03-05', '2030-03-01')).toBe('The end date must not be before the start date.')
  })

  it('asks for both dates and rejects nonsense', () => {
    expect(checkDateRange('', '2030-03-01')).toBe('Choose a start and an end date.')
    expect(checkDateRange('2030-03-01', '')).toBe('Choose a start and an end date.')
    expect(checkDateRange('not-a-date', '2030-03-01')).toBe('Enter valid dates.')
  })
})
