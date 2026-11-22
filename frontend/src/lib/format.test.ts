import { describe, expect, it } from 'vitest'
import { count, daysUntil, describeDue, formatDate, formatDateTime, formatDay, formatHour, formatHours, formatMoney, plural } from './format'

describe('plural', () => {
  it.each([
    ['entry', 'entries'],
    ['book', 'books'],
    ['reservation', 'reservations'],
    ['class', 'classes'],
    ['day', 'days'],
  ])('%s -> %s', (noun, expected) => {
    expect(plural(noun)).toBe(expected)
  })

  it('counts with the right form', () => {
    expect(count(1, 'book')).toBe('1 book')
    expect(count(0, 'book')).toBe('0 books')
    expect(count(2, 'copy')).toBe('2 copies')
  })
})

describe('money', () => {
  it('always shows two decimal places', () => {
    expect(formatMoney(0.5)).toMatch(/0\.50|0,50/)
    expect(formatMoney(10)).toMatch(/10\.00|10,00/)
  })
})

describe('dates', () => {
  it('formats in the requested time zone', () => {
    expect(formatDate('2030-03-01T23:30:00Z', 'UTC')).toMatch(/1/)
    expect(formatDate('2030-03-01T23:30:00Z', 'UTC')).toMatch(/2030/)
    expect(formatDateTime('2030-03-01T23:30:00Z', 'UTC')).toMatch(/23|11/)
  })
})

describe('due dates', () => {
  const now = Date.parse('2030-03-10T12:00:00Z')

  it('counts whole days, rounding a started day up', () => {
    expect(daysUntil('2030-03-13T12:00:00Z', now)).toBe(3)
    expect(daysUntil('2030-03-13T12:00:01Z', now)).toBe(4)
  })

  it.each([
    ['2030-03-14T12:00:00Z', 'due in 4 days'],
    ['2030-03-11T12:00:00Z', 'due tomorrow'],
    ['2030-03-10T18:00:00Z', 'due tomorrow'],
    ['2030-03-10T12:00:00Z', 'due today'],
    ['2030-03-09T12:00:00Z', '1 day overdue'],
    ['2030-03-07T12:00:00Z', '3 days overdue'],
  ])('%s -> %s', (iso, expected) => {
    expect(describeDue(iso, now)).toBe(expected)
  })
})

describe('chart labels', () => {
  it('shows a backend date as a short day and month, without drifting across time zones', () => {
    expect(formatDay('2030-03-05')).toMatch(/5/)
    expect(formatDay('2030-03-05')).toMatch(/Mar|3/)
    expect(formatDay('2030-12-31')).toMatch(/31/)
  })

  it('pads an hour', () => {
    expect(formatHour(9)).toBe('09:00')
    expect(formatHour(0)).toBe('00:00')
    expect(formatHour(23)).toBe('23:00')
  })
})

describe('formatHours', () => {
  it('shows one decimal place', () => {
    expect(formatHours(6.5)).toBe('6.5 h')
    expect(formatHours(0)).toBe('0.0 h')
    expect(formatHours(11.456)).toBe('11.5 h')
  })
})
