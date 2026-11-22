import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StatusBadge, statusLabel } from './StatusBadge'

describe('StatusBadge', () => {
  it.each([
    ['AVAILABLE', 'Available'],
    ['ON_LOAN', 'On loan'],
    ['READY', 'Ready to collect'],
    ['PENDING', 'In the queue'],
    ['OVERDUE', 'Overdue'],
    ['UNPAID', 'Unpaid'],
    ['OUT_OF_SERVICE', 'Out of service'],
  ])('shows %s as "%s"', (status, label) => {
    render(<StatusBadge status={status} />)

    expect(screen.getByText(label)).toBeInTheDocument()
  })

  it('falls back to the raw value for a status it does not know, rather than hiding it', () => {
    expect(statusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
    render(<StatusBadge status="SOMETHING_NEW" />)
    expect(screen.getByText('SOMETHING_NEW')).toBeInTheDocument()
  })

  it('colours problems red and good states green', () => {
    const { rerender } = render(<StatusBadge status="OVERDUE" />)
    expect(screen.getByText('Overdue').className).toMatch(/red/)

    rerender(<StatusBadge status="AVAILABLE" />)
    expect(screen.getByText('Available').className).toMatch(/emerald/)
  })
})
