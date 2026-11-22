import { describe, expect, it } from 'vitest'
import { describeAvailability } from './availability'

describe('describeAvailability', () => {
  it('says so when there are no copies at all', () => {
    expect(describeAvailability({ totalCopies: 0, availableCopies: 0, pendingReservations: 0 })).toEqual({
      text: 'No copies in circulation',
      tone: 'slate',
    })
  })

  it('shows how many copies are on the shelf', () => {
    expect(describeAvailability({ totalCopies: 4, availableCopies: 3, pendingReservations: 0 })).toEqual({
      text: '3 of 4 available',
      tone: 'green',
    })
  })

  it('says everything is out, and how many people are waiting', () => {
    expect(describeAvailability({ totalCopies: 2, availableCopies: 0, pendingReservations: 3 })).toEqual({
      text: 'All copies out, 3 waiting',
      tone: 'amber',
    })
  })

  it('does not mention a queue when nobody is waiting', () => {
    expect(describeAvailability({ totalCopies: 2, availableCopies: 0, pendingReservations: 0 }).text).toBe('All copies out')
    expect(describeAvailability({ totalCopies: 1, availableCopies: 0, pendingReservations: 2 }).text).toBe('The only copy is out, 2 waiting')
  })
})
